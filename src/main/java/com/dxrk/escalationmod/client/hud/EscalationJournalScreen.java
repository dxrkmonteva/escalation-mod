package com.dxrk.escalationmod.client.hud;

import com.dxrk.escalationmod.config.EscalationConfig;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.*;

public class EscalationJournalScreen extends Screen {

    private static final int NAME_ROW_HEIGHT = 14;
    private static final int DESC_LINE_HEIGHT = 10;
    private static final int PADDING = 12;
    private static final int LIST_TOP = 46;

    private final long staggerMs;
    private final long typewriterMsPerChar;

    private final Set<String> expandedPoolIds = new HashSet<>();
    private final List<RowBounds> rowBounds = new ArrayList<>();
    private final Map<String, Long> revealStartMs = new HashMap<>();
    private final long screenOpenedAtMs = System.currentTimeMillis();
    private boolean indexed = false;
    private boolean skipped = false;
    private int scrollOffset = 0;

    public EscalationJournalScreen() {
        super(Component.literal("Журнал усложнений"));
        EscalationConfig.UiSection ui = EscalationConfigManager.get().ui;
        this.staggerMs = (long) ui.journalStaggerMs;
        this.typewriterMsPerChar = (long) ui.journalTypewriterMsPerChar;
    }

    public void skipRevealAnimation() {
        skipped = true;
    }

    private void ensureIndexed(List<ClientHudState.JournalDisplayRow> sorted) {
        if (indexed) return;
        indexed = true;
        long t = screenOpenedAtMs;
        for (ClientHudState.JournalDisplayRow row : sorted) {
            revealStartMs.put(row.poolId, t);
            t += staggerMs;
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);

        List<ClientHudState.JournalDisplayRow> sorted = ClientHudState.INSTANCE.getJournalRows().stream()
                .sorted(Comparator.comparingLong(r -> r.firstSeenTick))
                .toList();
        ensureIndexed(sorted);

        int totalInstances = sorted.stream().mapToInt(ClientHudState.JournalDisplayRow::total).sum();

        gg.fill(PADDING - 6, PADDING - 6, this.width - PADDING + 6, LIST_TOP - 4, 0xC0181818);
        gg.fill(PADDING - 6, PADDING - 6, this.width - PADDING + 6, PADDING - 4, 0xFF55AAFF);

        gg.drawCenteredString(this.font, this.title, this.width / 2, PADDING, 0xFFFFFF);
        gg.drawCenteredString(this.font,
                "Активно: " + sorted.size() + " уникальных \u00b7 " + totalInstances + " всего",
                this.width / 2, PADDING + 12, 0x99AACCFF);

        rowBounds.clear();

        if (sorted.isEmpty()) {
            gg.drawCenteredString(this.font, "Пока нет активных усложнений.", this.width / 2, LIST_TOP + 10, 0xAAAAAA);
            super.render(gg, mouseX, mouseY, partialTick);
            return;
        }

        int listBottom = this.height - PADDING;
        int contentWidth = this.width - PADDING * 2;
        long now = System.currentTimeMillis();
        int y = LIST_TOP - scrollOffset;

        gg.enableScissor(0, LIST_TOP, this.width, listBottom);

        for (ClientHudState.JournalDisplayRow row : sorted) {
            long startsAt = revealStartMs.getOrDefault(row.poolId, screenOpenedAtMs);
            boolean started = skipped || now >= startsAt;

            if (!started) {
                if (y + NAME_ROW_HEIGHT > LIST_TOP && y < listBottom) {
                    gg.fill(PADDING, y + 2, PADDING + 60, y + NAME_ROW_HEIGHT - 2, 0x20FFFFFF);
                }
                rowBounds.add(new RowBounds(row.poolId, y, y + NAME_ROW_HEIGHT, false));
                y += NAME_ROW_HEIGHT;
                continue;
            }

            long sinceStart = skipped ? Long.MAX_VALUE : now - startsAt;
            int rowTop = y;
            boolean expanded = expandedPoolIds.contains(row.poolId);

            String tierTag = "[T" + row.tier + "]";
            String fullLine = tierTag + " " + row.name + (row.total() > 1 ? " \u00d7" + row.total() : "");
            int visibleChars;
            if (skipped || typewriterMsPerChar == 0L) {
                visibleChars = fullLine.length();
            } else {
                visibleChars = (int) Math.min(fullLine.length(), sinceStart / typewriterMsPerChar);
            }
            String shown = fullLine.substring(0, visibleChars);
            boolean lineDone = visibleChars >= fullLine.length();

            if (row.legendaryCount > 0) {
                int gold = pulsingGold(now);
                gg.fill(PADDING - 4, y, PADDING - 1, y + NAME_ROW_HEIGHT - 2, gold);
            }

            if (y + NAME_ROW_HEIGHT > LIST_TOP && y < listBottom) {
                int nameColor = expanded ? 0xFFE066 : 0xEEEEEE;
                gg.drawString(this.font, shown, PADDING, y, nameColor, false);

                if (lineDone) {
                    drawRarityDots(gg, this.font, row, PADDING + this.font.width(fullLine) + 8, y);
                }
            }
            y += NAME_ROW_HEIGHT;

            if (expanded && lineDone) {
                List<FormattedCharSequence> descLines = this.font.split(FormattedText.of(row.description), contentWidth - 10);
                for (FormattedCharSequence line : descLines) {
                    if (y + DESC_LINE_HEIGHT > LIST_TOP && y < listBottom) {
                        gg.drawString(this.font, line, PADDING + 10, y, 0xAAAAAA, false);
                    }
                    y += DESC_LINE_HEIGHT;
                }
            }

            rowBounds.add(new RowBounds(row.poolId, rowTop, y, lineDone));
        }

        gg.disableScissor();

        int totalHeight = (y + scrollOffset) - LIST_TOP;
        int visibleHeight = listBottom - LIST_TOP;
        if (totalHeight > visibleHeight) {
            int maxScroll = totalHeight - visibleHeight;
            scrollOffset = Math.min(scrollOffset, maxScroll);
            int barHeight = Math.max(10, visibleHeight * visibleHeight / totalHeight);
            int barY = LIST_TOP + (int) ((long) scrollOffset * (visibleHeight - barHeight) / Math.max(1, maxScroll));
            gg.fill(this.width - PADDING + 2, LIST_TOP, this.width - PADDING + 4, listBottom, 0x40FFFFFF);
            gg.fill(this.width - PADDING + 2, barY, this.width - PADDING + 4, barY + barHeight, 0xA0FFFFFF);
        } else {
            scrollOffset = 0;
        }

        super.render(gg, mouseX, mouseY, partialTick);
    }

    private void drawRarityDots(GuiGraphics gg, Font font, ClientHudState.JournalDisplayRow row, int x, int y) {
        int dotX = x;
        dotX = drawDot(gg, font, dotX, y, row.commonCount, 0xFFAAAAAA);
        dotX = drawDot(gg, font, dotX, y, row.rareCount, 0xFF5599FF);
        dotX = drawDot(gg, font, dotX, y, row.epicCount, 0xFFAA33CC);
        drawDot(gg, font, dotX, y, row.legendaryCount, pulsingGold(System.currentTimeMillis()));
    }

    private int drawDot(GuiGraphics gg, Font font, int x, int y, int count, int color) {
        if (count <= 0) return x;
        gg.fill(x, y + 3, x + 4, y + 7, color);
        String text = " " + count;
        gg.drawString(font, text, x + 6, y, color, false);
        return x + 6 + font.width(text) + 6;
    }

    private static int pulsingGold(long nowMs) {
        double phase = (nowMs % 1000) / 1000.0;
        int green = (int) (170 + 70 * Math.sin(phase * Math.PI * 2));
        return 0xFF000000 | (255 << 16) | (Math.max(0, Math.min(255, green)) << 8);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (RowBounds bounds : rowBounds) {
                if (!bounds.clickable) continue;
                if (mouseY >= bounds.top && mouseY < bounds.bottom
                        && mouseX >= PADDING && mouseX <= this.width - PADDING) {
                    if (!expandedPoolIds.remove(bounds.poolId)) {
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

    private record RowBounds(String poolId, int top, int bottom, boolean clickable) {
    }
}
