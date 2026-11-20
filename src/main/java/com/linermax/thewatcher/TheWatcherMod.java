package com.linermax.thewatcher;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Map;

public class TheWatcherMod implements ModInitializer {
    public static final String MOD_ID = "the_watcher";
    private static final Map<String, Long> LAST_NIGHT_EVENT = new HashMap<>();
    private static final Map<String, Integer> TICKS = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerTickEvents.END_WORLD_TICK.register(TheWatcherMod::tickWorld);
        System.out.println("[THE WATCHER] The night is listening.");
    }

    private static void tickWorld(ServerWorld world) {
        long time = world.getTimeOfDay();
        long day = time / 24000L;
        long clock = time % 24000L;
        boolean night = clock >= 13000L && clock <= 23000L;
        if (!night || day < 2) return;
        String key = world.getRegistryKey().getValue().toString();
        int tick = TICKS.merge(key, 1, Integer::sum);
        if (tick % 20 != 0) return;
        if (LAST_NIGHT_EVENT.getOrDefault(key, -1L) == day) return;
        if (world.getRandom().nextInt(100) >= Math.min(28 + (int) day * 5, 75)) return;
        LAST_NIGHT_EVENT.put(key, day);
        int nightNumber = (int) day + 1;
        for (var player : world.getPlayers()) {
            if (player.isSpectator() || player.isCreative()) continue;
            runNightEvent(world, player, nightNumber);
        }
    }

    private static void runNightEvent(ServerWorld world, net.minecraft.server.network.ServerPlayerEntity player, int night) {
        int roll = world.getRandom().nextInt(100);
        if (night <= 3) {
            spawnWatcher(world, player, false);
            player.sendMessage(Text.literal("§7Тобі здається, що хтось дивиться з-за дерев..."), false);
            return;
        }
        if (night <= 5) {
            if (roll < 55) {
                spawnWatcher(world, player, false);
                player.sendMessage(Text.literal("§8За вікном щось завмерло."), false);
            } else {
                playKnock(world, player);
                player.sendMessage(Text.literal("§7*тук... тук...*"), false);
            }
            return;
        }
        if (night == 10 || roll < 22) {
            phoneCall(world, player);
        } else if (roll < 58) {
            breakNearbyWindowOrDoor(world, player);
            player.sendMessage(Text.literal("§cЩОСЬ ПРОНИКЛО ДО БУДИНКУ!"), false);
            spawnWatcher(world, player, true);
        } else if (roll < 82) {
            playKnock(world, player);
            player.sendMessage(Text.literal("§4Стук у двері. Але ти нікого не бачиш."), false);
        } else {
            spawnWatcher(world, player, true);
            player.sendMessage(Text.literal("§4БІЖИ."), false);
        }
    }

    private static void spawnWatcher(ServerWorld world, net.minecraft.server.network.ServerPlayerEntity player, boolean close) {
        if (!world.getEntitiesByClass(ZombieEntity.class, player.getBoundingBox().expand(42),
                z -> z.isAlive() && z.getCustomName() != null && z.getCustomName().getString().equals("THE WATCHER")).isEmpty()) return;
        double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
        double distance = close ? 7 + world.getRandom().nextDouble() * 5 : 15 + world.getRandom().nextDouble() * 9;
        double x = player.getX() + Math.cos(angle) * distance;
        double z = player.getZ() + Math.sin(angle) * distance;
        BlockPos pos = BlockPos.ofFloored(x, player.getY(), z);
        BlockPos surface = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos);
        ZombieEntity watcher = EntityType.ZOMBIE.create(world);
        if (watcher == null) return;
        watcher.refreshPositionAndAngles(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5, world.getRandom().nextFloat() * 360f, 0);
        watcher.setCustomName(Text.literal("THE WATCHER"));
        watcher.setCustomNameVisible(false);
        watcher.setPersistent();
        watcher.setCanPickUpLoot(false);
        watcher.setTarget(player);
        watcher.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(close ? 0.34 : 0.27);
        watcher.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(34.0);
        watcher.setHealth(34.0f);
        world.spawnEntity(watcher);
    }

    private static void phoneCall(ServerWorld world, net.minecraft.server.network.ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§c§lВХІДНИЙ ДЗВІНОК"), false);
        player.sendMessage(Text.literal("§8Невідомий номер: §f..."), false);
        player.sendMessage(Text.literal("§7§oНе відповідай. Він уже в будинку."), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.AMBIENT, 1.4f, 0.65f);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.AMBIENT, 1.2f, 0.55f);
    }

    private static void playKnock(ServerWorld world, net.minecraft.server.network.ServerPlayerEntity player) {
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1.8f, 0.55f);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1.8f, 0.48f);
    }

    private static void breakNearbyWindowOrDoor(ServerWorld world, net.minecraft.server.network.ServerPlayerEntity player) {
        BlockPos center = player.getBlockPos();
        for (int attempt = 0; attempt < 35; attempt++) {
            int dx = world.getRandom().nextInt(13) - 6;
            int dy = world.getRandom().nextInt(5) - 2;
            int dz = world.getRandom().nextInt(13) - 6;
            BlockPos pos = center.add(dx, dy, dz);
            BlockState state = world.getBlockState(pos);
            boolean glass = state.isOf(Blocks.GLASS) || state.isOf(Blocks.GLASS_PANE);
            boolean door = state.getBlock() instanceof DoorBlock;
            if (glass || door) {
                world.breakBlock(pos, true, player);
                return;
            }
        }
    }
}