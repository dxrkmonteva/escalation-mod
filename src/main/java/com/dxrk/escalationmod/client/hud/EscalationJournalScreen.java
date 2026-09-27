package com.dxrk.escalationmod.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

/** Журнал усложнений по клавише J — сгруппировано по id, FIFO-порядок (раздел 10). */
public class EscalationJournalScreen extends Screen {

    private static final int ROW_HEIGHT = 14;
    private static final int PADDING = 12;

    public EscalationJournalScreen() {
        super(Component.literal("Журнал усложнений"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, PADDING, 0xFFFFFF);

        List<ClientHudState.JournalDisplayRow> rows = ClientHudState.INSTANCE.getJournalRows();
        List<ClientHudState.JournalDisplayRow> sorted = rows.stream()
                .sorted(Comparator.comparingLong(r -> r.firstSeenTick))
                .toList();

        if (sorted.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Пока нет активных усложнений.", this.width / 2, PADDING + 24, 0xAAAAAA);
        } else {
            int y = PADDING + 24;
            for (ClientHudState.JournalDisplayRow row : sorted) {
                String line = row.name + (row.count > 1 ? " \u00d7" + row.count : "");
                guiGraphics.drawString(this.font, line, PADDING, y, 0xEEEEEE, false);
                y += ROW_HEIGHT;
                if (y > this.height - PADDING) break; // без скролла пока — влезает сколько влезает
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
