package com.dxrk.escalationmod.client.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/** Кастомный оверлей тостов — левый верх, рост/сжатие, печатающийся текст, рамка по редкости. */
public final class EscalationToastOverlay implements IGuiOverlay {

    private static final int MARGIN_X = 4;
    private static final int MARGIN_Y = 4;
    private static final int GAP_Y = 4;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientToastManager.INSTANCE.hasAny()) return;

        Font font = Minecraft.getInstance().font;
        int y = MARGIN_Y;

        for (ActiveEscalationToast toast : ClientToastManager.INSTANCE.visibleToasts()) {
            int fullHeight = toast.getFullContentHeight(font);
            int height = (int) (fullHeight * toast.getScaleProgress());
            if (height <= 1) {
                continue;
            }
            drawToast(guiGraphics, font, toast, MARGIN_X, y, ActiveEscalationToast.WIDTH, height, fullHeight);
            y += fullHeight + GAP_Y;
        }
    }

    private void drawToast(GuiGraphics gg, Font font, ActiveEscalationToast toast,
                            int x, int y, int width, int height, int fullHeight) {
        int borderColor = rarityColor(toast.rarityKey, toast.elapsedMs());

        gg.fill(x, y, x + width, y + height, 0xE0202020);
        gg.fill(x, y, x + width, y + 2, borderColor);
        gg.fill(x, y, x + 2, y + height, borderColor);
        gg.fill(x + width - 2, y, x + width, y + height, borderColor);
        if (height >= fullHeight - 1) {
            gg.fill(x, y + height - 2, x + width, y + height, borderColor);
        }

        if (height < fullHeight - 2) {
            return; // бокс ещё растёт — текст пока не рисуем, чтобы не вылезал за рамку
        }

        gg.enableScissor(x, y, x + width, y + height);

        int pad = ActiveEscalationToast.PADDING;
        int lh = ActiveEscalationToast.LINE_HEIGHT;
        int ty = y + pad;

        gg.drawString(font, "\u26a0 " + toast.title, x + pad, ty, borderColor, false);
        ty += lh;
        gg.drawString(font, toast.name, x + pad, ty, 0xFFFFFF, false);
        ty += lh;

        int visibleChars = toast.getTypewriterVisibleChars();
        List<FormattedCharSequence> lines = toast.getWrappedLines(font, width - pad * 2);
        int shown = 0;
        for (FormattedCharSequence line : lines) {
            int lineLen = countChars(line);
            if (shown >= visibleChars) break;
            gg.drawString(font, line, x + pad, ty, 0xCCCCCC, false);
            ty += lh;
            shown += lineLen;
        }

        if (toast.legendary) {
            gg.drawString(font, "усиление: " + toast.getLegendaryEscalationSeconds() + "/"
                    + ActiveEscalationToast.LEGENDARY_ESCALATION_CAP_SEC + " сек", x + pad, ty, 0xFFD700, false);
        }

        gg.disableScissor();
    }

    private static int countChars(FormattedCharSequence seq) {
        int[] count = {0};
        seq.accept((index, style, codePoint) -> {
            count[0]++;
            return true;
        });
        return count[0];
    }

    private static int rarityColor(String rarityKey, long elapsedMs) {
        return switch (rarityKey) {
            case "common" -> 0xFFAAAAAA;
            case "rare" -> 0xFF5599FF;
            case "epic" -> 0xFFAA33CC;
            case "legendary" -> pulsingGold(elapsedMs);
            default -> 0xFFFFFFFF;
        };
    }

    private static int pulsingGold(long elapsedMs) {
        double phase = (elapsedMs % 1000) / 1000.0;
        int green = (int) (170 + 70 * Math.sin(phase * Math.PI * 2));
        return 0xFF000000 | (255 << 16) | (Math.max(0, Math.min(255, green)) << 8);
    }
}
