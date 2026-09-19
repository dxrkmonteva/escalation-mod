package com.dxrk.escalationmod.client.toast;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Рантайм-состояние одного показанного тоста (раздел 10 спеки). */
public class ActiveEscalationToast {

    public static final long GROW_MS = 220L;
    public static final long HOLD_MS_MIN = 5000L;
    public static final long HOLD_MS_MAX = 8000L;
    public static final long SHRINK_MS = 180L;
    public static final long TYPEWRITER_MS_PER_CHAR = 18L;
    public static final int LEGENDARY_ESCALATION_CAP_SEC = 30;

    public static final int WIDTH = 220;
    public static final int PADDING = 6;
    public static final int LINE_HEIGHT = 10;

    public final int tier;
    public final String rarityKey; // "common" | "rare" | "epic" | "legendary"
    public final String title;
    public final String name;
    public final String description;
    public final boolean legendary;

    private final long spawnTimeMs;
    private final long holdDurationMs;
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
        long span = HOLD_MS_MAX - HOLD_MS_MIN;
        this.holdDurationMs = HOLD_MS_MIN + (long) (Math.random() * span);
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
            return sinceSkip >= SHRINK_MS ? ToastPhase.DONE : ToastPhase.SHRINKING;
        }
        if (elapsed < GROW_MS) return ToastPhase.GROWING;
        if (elapsed < GROW_MS + holdDurationMs) return ToastPhase.HOLDING;
        if (elapsed < GROW_MS + holdDurationMs + SHRINK_MS) return ToastPhase.SHRINKING;
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
            t = Math.min(1f, elapsed / (float) GROW_MS);
            return easeOutCubic(t);
        }
        if (phase == ToastPhase.HOLDING) {
            return 1f;
        }
        if (phase == ToastPhase.SHRINKING) {
            long shrinkStart = skipRequestedAtMs >= 0
                    ? skipRequestedAtMs
                    : spawnTimeMs + GROW_MS + holdDurationMs;
            long sinceShrink = System.currentTimeMillis() - shrinkStart;
            t = Math.min(1f, sinceShrink / (float) SHRINK_MS);
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
        long sinceHoldStart = elapsedMs() - GROW_MS;
        if (sinceHoldStart < 0) return 0;
        int chars = (int) (sinceHoldStart / TYPEWRITER_MS_PER_CHAR);
        return Math.min(description.length(), Math.max(0, chars));
    }

    public int getLegendaryEscalationSeconds() {
        long sec = elapsedMs() / 1000L;
        return (int) Math.min(LEGENDARY_ESCALATION_CAP_SEC, sec);
    }
}
