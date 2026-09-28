package com.dxrk.escalationmod.client.hud;

import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.network.EscalationHudSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Таймер-бар вверху экрана: след. усложнение / разблокировка измерений / Mercy-пауза / победа / endless. */
public final class EscalationTimerBarOverlay implements IGuiOverlay {

    private static final int LINE_HEIGHT = 10;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        ClientHudState state = ClientHudState.INSTANCE;
        if (!state.hasData()) return;

        Font font = Minecraft.getInstance().font;
        int y = 4;

        int runState = state.getRunState();

        if (runState == EscalationHudSyncPacket.RUN_WON_STOPPED) {
            drawLine(guiGraphics, font, y, screenWidth, "\u2726 Победа \u2014 новые усложнения остановлены", 0xFFFFD700);
            y += LINE_HEIGHT;
        } else if (state.isMercyPauseActive()) {
            drawLine(guiGraphics, font, y, screenWidth,
                    "\uD83D\uDEE1 Передышка: " + formatTicks(state.getMercyPauseRemainingTicks()), 0xFF55AAFF);
            y += LINE_HEIGHT;
        } else {
            long remaining = state.getNextSpawnRemainingTicks();
            String prefix = runState == EscalationHudSyncPacket.RUN_ENDLESS ? "\u221E " : "\u23F3 ";
            drawLine(guiGraphics, font, y, screenWidth,
                    prefix + "Следующее усложнение через: " + formatTicks(remaining), proximityColor(remaining));
            y += LINE_HEIGHT;
        }

        if (!state.isNetherUnlocked()) {
            drawLine(guiGraphics, font, y, screenWidth,
                    "\uD83D\uDD12 Nether: доступен через " + formatTicks(state.getNetherRemainingTicks()), 0xFFAAAAAA);
            y += LINE_HEIGHT;
        }

        String endStatus = state.getEndStatus();
        if (!"unlocked".equals(endStatus)) {
            String text = "locked_soon".equals(endStatus) ? "\uD83D\uDD12 End: уже скоро" : "\uD83D\uDD12 End: ещё долго";
            drawLine(guiGraphics, font, y, screenWidth, text, 0xFFAAAAAA);
        }
    }

    private void drawLine(GuiGraphics gg, Font font, int y, int screenWidth, String text, int color) {
        int drawX = (screenWidth - font.width(text)) / 2;
        gg.drawString(font, text, drawX, y, color, true);
    }

    /** Зелёный -> жёлтый -> красный по приближению события (пороги из конфига ui.timerBar*). */
    private static int proximityColor(long remainingTicks) {
        EscalationConfig.UiSection ui = EscalationConfigManager.get().ui;
        long seconds = remainingTicks / 20L;
        if (seconds <= ui.timerBarRedBelowSec) return 0xFFFF5555;
        if (seconds <= ui.timerBarYellowBelowSec) return 0xFFFFFF55;
        return 0xFF55FF55;
    }

    private static String formatTicks(long ticks) {
        long totalSeconds = Math.max(0, ticks / 20L);
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) {
            return String.format("%dч %02dм %02dс", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }
}
