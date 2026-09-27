package com.dxrk.escalationmod.client.toast;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.client.hud.EscalationJournalScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Escalation.MODID, value = Dist.CLIENT)
public final class ToastInputHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ClientToastManager.INSTANCE.tick();

        while (EscalationClientKeybinds.SKIP_TOAST.consumeClick()) {
            if (Minecraft.getInstance().screen instanceof EscalationJournalScreen journalScreen) {
                journalScreen.skipRevealAnimation();
            } else {
                ClientToastManager.INSTANCE.skipOldestActive();
            }
        }

        while (EscalationClientKeybinds.OPEN_JOURNAL.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(new EscalationJournalScreen());
            }
        }
    }
}
