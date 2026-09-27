package com.dxrk.escalationmod.client.toast;

import com.dxrk.escalationmod.Escalation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Escalation.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EscalationClientKeybinds {

    public static final String CATEGORY = "key.categories.escalation";

    public static final KeyMapping SKIP_TOAST = new KeyMapping(
            "key.escalation.skip_toast", InputConstants.Type.KEYSYM, InputConstants.KEY_X, CATEGORY);

    public static final KeyMapping OPEN_JOURNAL = new KeyMapping(
            "key.escalation.open_journal", InputConstants.Type.KEYSYM, InputConstants.KEY_J, CATEGORY);

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SKIP_TOAST);
        event.register(OPEN_JOURNAL);
    }
}
