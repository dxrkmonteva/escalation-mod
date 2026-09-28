package com.dxrk.escalationmod.client.toast;

import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Рантайм-состояние одного показанного тоста. Тайминги — из конфига (ui.toast*), фиксируются при создании. */
public class ActiveEscalationToast {

    public static final int WIDTH = 220;
    public static final int PADDING = 6;
    public static final int LINE_HEIGHT = 10;

    public final int tier;
    public final String rarityKey;
    public final String title;
    public final String name;
    public final String description;
    public final boolean legendary;

    private final long spawnTimeMs;
    private final long growMs;
    private final long holdDurationMs;
    private final long shrinkMs;
    private final long typewriterMsPerChar;
    private long skipRequestedAtMs = -1L;

    private List<FormattedCharSequence> cachedLines;
    private int cachedForWidth = -1;

    public ActiveEscalationToast(int tier, String rarityKey, String title,
                                  String name, String description, boolean legendary) {
        this.tier = tier;
        this.rarityKey = rarityKey;
        this.title = title;
        this.name = name;
        this.description = description;
        this.legendary = legendary;
        this.spawnTimeMs = System.currentTimeMillis();

        EscalationConfig.UiSection ui = EscalationConfigManager.get().ui;
        this.growMs = Math.max(1L, (long) ui.toastGrowMs);
        this.shrinkMs = Math.max(1L, (long) ui.toastShrinkMs);
        this.typewriterMsPerChar = Math.max(0L, (long) ui.toastTypewriterMsPerChar);
        double holdSec = ui.toastHoldMinSec + Math.random() * (ui.toastHoldMaxSec - ui.toastHoldMinSec);
        this.holdDurationMs = (long) (holdSec * 1000.0);
    }

    /** Потолок счётчика эскалации легендарки на тосте (rarity.legendaryEscalation.durationSec). */
    public static int escalationCapSec() {
        return Math.max(1, EscalationConfigManager.get().rarity.legendaryEscalation.durationSec);
    }

    public List<FormattedCharSequence> getWrappedLines(Font font, int maxWidth) {
        if (cachedLines == null || cachedForWidth != maxWidth) {
            cachedLines = font.split(FormattedText.of(description), maxWidth);
            cachedForWidth = maxWidth;
        }
        return cachedLines;
    }

    public int getFullContentHeight(Font font) {
        List<FormattedCharSequence> lines = getWrappedLines(font, WIDTH - PADDING * 2);
        int rows = 2 + lines.size() + (legendary ? 1 : 0);
        return PADDING * 2 + rows * LINE_HEIGHT;
    }

    public long elapsedMs() {
        return System.currentTimeMillis() - spawnTimeMs;
    }

    public void requestSkip() {
        if (skipRequestedAtMs < 0 && getPhase() != ToastPhase.DONE) {
            skipRequestedAtMs = System.currentTimeMillis();
        }
    }

    public ToastPhase getPhase() {
        long elapsed = elapsedMs();
        if (skipRequestedAtMs >= 0) {
            long sinceSkip = System.currentTimeMillis() - skipRequestedAtMs;
            return sinceSkip >= shrinkMs ? ToastPhase.DONE : ToastPhase.SHRINKING;
        }
        if (elapsed < growMs) return ToastPhase.GROWING;
        if (elapsed < growMs + holdDurationMs) return ToastPhase.HOLDING;
        if (elapsed < growMs + holdDurationMs + shrinkMs) return ToastPhase.SHRINKING;
        return ToastPhase.DONE;
    }

    public boolean isFinished() {
        return getPhase() == ToastPhase.DONE;
    }

    public float getScaleProgress() {
        ToastPhase phase = getPhase();
        long elapsed = elapsedMs();
        float t;
        if (phase == ToastPhase.GROWING) {
            t = Math.min(1f, elapsed / (float) growMs);
            return easeOutCubic(t);
        }
        if (phase == ToastPhase.HOLDING) {
            return 1f;
        }
        if (phase == ToastPhase.SHRINKING) {
            long shrinkStart = skipRequestedAtMs >= 0
                    ? skipRequestedAtMs
                    : spawnTimeMs + growMs + holdDurationMs;
            long sinceShrink = System.currentTimeMillis() - shrinkStart;
            t = Math.min(1f, sinceShrink / (float) shrinkMs);
            return 1f - easeOutCubic(t);
        }
        return 0f;
    }

    private static float easeOutCubic(float t) {
        float f = t - 1f;
        return f * f * f + 1f;
    }

    public int getTypewriterVisibleChars() {
        if (getPhase() == ToastPhase.GROWING) return 0;
        long sinceHoldStart = elapsedMs() - growMs;
        if (sinceHoldStart < 0) return 0;
        if (typewriterMsPerChar == 0L) return description.length();
        int chars = (int) (sinceHoldStart / typewriterMsPerChar);
        return Math.min(description.length(), Math.max(0, chars));
    }

    public int getLegendaryEscalationSeconds() {
        long sec = elapsedMs() / 1000L;
        return (int) Math.min(escalationCapSec(), sec);
    }
}
