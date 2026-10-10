package com.dxrk.escalationmod.behavior;

import com.dxrk.escalationmod.Escalation;
import com.dxrk.escalationmod.config.EscalationConfigManager;
import com.dxrk.escalationmod.network.ClientBehaviorPacket;
import com.dxrk.escalationmod.network.EscalationNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * ЕДИНСТВЕННЫЙ диспетчер событий Forge для behavioral-усложнений: находит игрока и вызывает
 * только обработчики его АКТИВНЫХ id (а не 1056 подписчиков).
 */
@Mod.EventBusSubscriber(modid = Escalation.MODID)
public final class BehaviorEvents {

    private static final Map<UUID, AbstractVillager> LAST_TRADER = new HashMap<>();
    private static final Map<UUID, Map<String, Float>> LAST_SENT = new HashMap<>();

    private BehaviorEvents() {
    }

    // ------------------------------------------------------------------ раз в секунду

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!EscalationConfigManager.get().behavior.enabled) return;
        if (event.getServer().getTickCount() % 20 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            List<BehaviorGroup> groups = BehaviorManager.groupsOf(player);
            Map<String, Float> client = new TreeMap<>();
            for (BehaviorGroup g : groups) {
                BehaviorManager.safe(g.poolId, () -> g.handler.onSecond(player, g));
                BehaviorManager.safe(g.poolId, () -> g.handler.contributeClientState(player, g, client));
            }
            syncClient(player, client);
        }
    }

    private static void syncClient(ServerPlayer player, Map<String, Float> current) {
        Map<String, Float> prev = LAST_SENT.get(player.getUUID());
        Map<String, Float> before = prev == null ? Map.of() : prev;
        if (before.equals(current)) return;
        LAST_SENT.put(player.getUUID(), new HashMap<>(current));
        EscalationNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientBehaviorPacket(new HashMap<>(current)));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        BehaviorManager.forget(id);
        LAST_TRADER.remove(id);
        LAST_SENT.remove(id);
    }

    // ------------------------------------------------------------------ события

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();
        var pos = event.getPos();
        BehaviorManager.dispatch(Trigger.BLOCK_BREAK, player, c -> {
            c.block = state;
            c.pos = pos;
        }, null);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        float amount = event.getAmount();
        Entity source = event.getSource().getEntity();

        if (event.getEntity() instanceof ServerPlayer victim) {
            LivingEntity attacker = source instanceof LivingEntity le ? le : null;
            BehaviorManager.dispatch(Trigger.PLAYER_HURT, victim, c -> {
                c.amount = amount;
                c.other = attacker;
            }, null);
        }
        if (source instanceof ServerPlayer attackerPlayer && event.getEntity() != attackerPlayer) {
            LivingEntity target = event.getEntity();
            BehaviorManager.dispatch(Trigger.PLAYER_ATTACK, attackerPlayer, c -> {
                c.amount = amount;
                c.other = target;
            }, null);
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer && event.getEntity() != killer) {
            LivingEntity victim = event.getEntity();
            BehaviorManager.dispatch(Trigger.ENTITY_KILLED, killer, c -> c.other = victim, null);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafted = event.getCrafting();
        BehaviorManager.dispatch(Trigger.ITEM_CRAFTED, player, c -> c.stack = crafted, null);
    }

    @SubscribeEvent
    public static void onDeathDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            boolean[] remove = {false};
            BehaviorManager.dispatch(Trigger.DEATH_DROPS, player, c -> {
                c.drop = drop;
                c.stack = drop.getItem();
            }, c -> {
                if (c.removeDrop) remove[0] = true;
            });
            if (remove[0]) it.remove();
        }
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BehaviorManager.dispatch(Trigger.SLEEP_ATTEMPT, player, null, c -> {
            if (c.cancelSleep) event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
        });
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        ItemStack held = event.getItemStack();
        var pos = event.getPos();
        BehaviorManager.dispatch(Trigger.RIGHT_CLICK_BLOCK, player, c -> {
            c.block = state;
            c.pos = pos;
            c.stack = held;
        }, c -> {
            if (c.cancel) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        BehaviorManager.dispatch(Trigger.ANVIL_USE, player, c -> {
            c.stack = left;
            c.stack2 = right;
        }, null);
    }

    // ------------------------------------------------------------------ торговля

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getTarget() instanceof AbstractVillager villager && event.getEntity() instanceof ServerPlayer player) {
            LAST_TRADER.put(player.getUUID(), villager);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof MerchantMenu)) return;
        AbstractVillager villager = LAST_TRADER.get(player.getUUID());
        if (villager == null) return;

        try {
            VillagerNumericHooks.apply(player, villager);
        } catch (Throwable t) {
            BehaviorRegistry.warnOnce("VillagerNumericHooks: " + t);
        }
        BehaviorManager.dispatch(Trigger.MERCHANT_OPEN, player, c -> c.villager = villager, null);
    }
}
