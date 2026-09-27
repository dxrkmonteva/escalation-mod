package com.dxrk.escalationmod.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Таймер-бар вверху экрана: след. усложнение / разблокировка измерений / Mercy-пауза (раздел 10). */
public final class EscalationTimerBarOverlay implements IGuiOverlay {

    private static final int BAR_WIDTH = 200;
    private static final int LINE_HEIGHT = 10;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientHudState.INSTANCE.hasData()) return;

        Font font = Minecraft.getInstance().font;
        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = 4;

        if (ClientHudState.INSTANCE.isMercyPauseActive()) {
            drawLine(guiGraphics, font, x, y, screenWidth,
                    "\uD83D\uDEE1 Передышка: " + formatTicks(ClientHudState.INSTANCE.getMercyPauseRemainingTicks()),
                    0xFF55AAFF);
        } else {
            long remaining = ClientHudState.INSTANCE.getNextSpawnRemainingTicks();
            drawLine(guiGraphics, font, x, y, screenWidth,
                    "\u23F3 Следующее усложнение через: " + formatTicks(remaining),
                    proximityColor(remaining));
            y += LINE_HEIGHT;
        }

        y += LINE_HEIGHT;

        if (!ClientHudState.INSTANCE.isNetherUnlocked()) {
            drawLine(guiGraphics, font, x, y, screenWidth,
                    "\uD83D\uDD12 Nether: доступен через " + formatTicks(ClientHudState.INSTANCE.getNetherRemainingTicks()),
                    0xFFAAAAAA);
            y += LINE_HEIGHT;
        }

        String endStatus = ClientHudState.INSTANCE.getEndStatus();
        if (!"unlocked".equals(endStatus)) {
            String text = "locked_soon".equals(endStatus) ? "\uD83D\uDD12 End: уже скоро" : "\uD83D\uDD12 End: ещё долго";
            drawLine(guiGraphics, font, x, y, screenWidth, text, 0xFFAAAAAA);
        }
    }

    private void drawLine(GuiGraphics gg, Font font, int x, int y, int screenWidth, String text, int color) {
        int width = font.width(text);
        int drawX = (screenWidth - width) / 2;
        gg.drawString(font, text, drawX, y, color, true);
    }

    /** Зелёный -> жёлтый -> красный (последние 10 сек) по приближению события. */
    private static int proximityColor(long remainingTicks) {
        long seconds = remainingTicks / 20L;
        if (seconds <= 10) return 0xFFFF5555;
        if (seconds <= 30) return 0xFFFFFF55;
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
