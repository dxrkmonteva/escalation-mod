package com.dxrk.escalationmod;

import com.dxrk.escalationmod.pool.PoolRegistry;
import com.dxrk.escalationmod.runtime.RuntimeSmokeTest;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(Escalation.MODID)
public class Escalation {

    public static final String MODID = "escalation";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Escalation() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        LOGGER.info("Escalation mod: конструктор вызван, регистрация прошла успешно.");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Escalation mod: commonSetup — загружаю пул усложнений...");
        PoolRegistry.init();
        RuntimeSmokeTest.run();
    }
}
