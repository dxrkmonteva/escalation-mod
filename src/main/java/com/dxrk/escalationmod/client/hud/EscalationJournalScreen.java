package com.dxrk.escalationmod.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Журнал усложнений по клавише J — сгруппировано по id, FIFO-порядок (раздел 10).
 * Клик по строке разворачивает/сворачивает описание. Списки со скроллом — раскрытые
 * описания легко не влезают в экран без него.
 */
public class EscalationJournalScreen extends Screen {

    private static final int NAME_ROW_HEIGHT = 14;
    private static final int DESC_LINE_HEIGHT = 10;
    private static final int PADDING = 12;
    private static final int LIST_TOP = 40;

    private final Set<String> expandedPoolIds = new HashSet<>();
    private final List<RowBounds> rowBounds = new ArrayList<>();
    private int scrollOffset = 0;

    public EscalationJournalScreen() {
        super(Component.literal("Журнал усложнений"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, PADDING, 0xFFFFFF);

        List<ClientHudState.JournalDisplayRow> sorted = ClientHudState.INSTANCE.getJournalRows().stream()
                .sorted(Comparator.comparingLong(r -> r.firstSeenTick))
                .toList();

        rowBounds.clear();

        if (sorted.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Пока нет активных усложнений.", this.width / 2, LIST_TOP, 0xAAAAAA);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        int listBottom = this.height - PADDING;
        int contentWidth = this.width - PADDING * 2;
        int y = LIST_TOP - scrollOffset;

        guiGraphics.enableScissor(0, LIST_TOP, this.width, listBottom);

        for (ClientHudState.JournalDisplayRow row : sorted) {
            int rowTop = y;
            boolean expanded = expandedPoolIds.contains(row.poolId);

            String nameLine = row.name + (row.count > 1 ? " \u00d7" + row.count : "");
            if (y + NAME_ROW_HEIGHT > LIST_TOP && y < listBottom) {
                int nameColor = expanded ? 0xFFE066 : 0xEEEEEE;
                guiGraphics.drawString(this.font, nameLine, PADDING, y, nameColor, false);
            }
            y += NAME_ROW_HEIGHT;

            List<FormattedCharSequence> descLines = List.of();
            if (expanded) {
                descLines = this.font.split(FormattedText.of(row.description), contentWidth - 10);
                for (FormattedCharSequence line : descLines) {
                    if (y + DESC_LINE_HEIGHT > LIST_TOP && y < listBottom) {
                        guiGraphics.drawString(this.font, line, PADDING + 10, y, 0xAAAAAA, false);
                    }
                    y += DESC_LINE_HEIGHT;
                }
            }

            rowBounds.add(new RowBounds(row.poolId, rowTop, y));
        }

        guiGraphics.disableScissor();

        int totalHeight = (y + scrollOffset) - LIST_TOP;
        int visibleHeight = listBottom - LIST_TOP;
        if (totalHeight > visibleHeight) {
            int maxScroll = totalHeight - visibleHeight;
            int barHeight = Math.max(10, visibleHeight * visibleHeight / totalHeight);
            int barY = LIST_TOP + (int) ((long) scrollOffset * (visibleHeight - barHeight) / Math.max(1, maxScroll));
            guiGraphics.fill(this.width - PADDING + 2, LIST_TOP, this.width - PADDING + 4, listBottom, 0x40FFFFFF);
            guiGraphics.fill(this.width - PADDING + 2, barY, this.width - PADDING + 4, barY + barHeight, 0xA0FFFFFF);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (RowBounds bounds : rowBounds) {
                if (mouseY >= bounds.top && mouseY < bounds.bottom
                        && mouseX >= PADDING && mouseX <= this.width - PADDING) {
                    if (expandedPoolIds.contains(bounds.poolId)) {
                        expandedPoolIds.remove(bounds.poolId);
                    } else {
                        expandedPoolIds.add(bounds.poolId);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset = Math.max(0, scrollOffset - (int) (delta * DESC_LINE_HEIGHT * 2));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RowBounds(String poolId, int top, int bottom) {
    }
}
