package com.dxrk.escalationmod.client.toast;

import com.dxrk.escalationmod.Escalation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Skip по клавише X — клик мышью недоступен в геймплее (курсор захвачен камерой). */
@Mod.EventBusSubscriber(modid = Escalation.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EscalationClientKeybinds {

    public static final String CATEGORY = "key.categories.escalation";

    public static final KeyMapping SKIP_TOAST = new KeyMapping(
            "key.escalation.skip_toast",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_X,
            CATEGORY
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SKIP_TOAST);
    }
}
