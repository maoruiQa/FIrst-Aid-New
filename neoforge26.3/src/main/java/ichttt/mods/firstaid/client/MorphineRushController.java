/*
 * FirstAid
 * Copyright (C) 2017-2024
 */

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * First ~10 seconds after morphine activates: layered random "rush" stingers.
 */
public final class MorphineRushController {
    private static final List<Identifier> RUSH_SOUND_IDS = List.of(
            Identifier.fromNamespaceAndPath(FirstAid.MODID, "debuff.heartbeat"),
            Identifier.fromNamespaceAndPath(FirstAid.MODID, "debuff.tinnitus"),
            Identifier.withDefaultNamespace("block.beacon.activate"),
            Identifier.withDefaultNamespace("block.beacon.ambient"),
            Identifier.withDefaultNamespace("block.beacon.power_select"),
            Identifier.withDefaultNamespace("block.respawn_anchor.charge"),
            Identifier.withDefaultNamespace("entity.player.levelup"),
            Identifier.withDefaultNamespace("block.enchantment_table.use"),
            Identifier.withDefaultNamespace("block.amethyst_block.chime"),
            Identifier.withDefaultNamespace("entity.experience_orb.pickup"),
            Identifier.withDefaultNamespace("entity.allay.ambient_with_item")
    );

    private int lastRushTicks = 0;
    private int nextStingerTicks = 0;
    private int onsetPlayedForSession = 0;

    public void tick(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null || !player.isAlive() || !FirstAid.isSynced) {
            lastRushTicks = 0;
            nextStingerTicks = 0;
            onsetPlayedForSession = 0;
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        PlayerDamageModel model = damageModel instanceof PlayerDamageModel m ? m : null;
        int rushTicks = model == null ? 0 : model.getMorphineRushTicks();

        if (rushTicks <= 0) {
            lastRushTicks = 0;
            nextStingerTicks = 0;
            onsetPlayedForSession = 0;
            return;
        }

        if (!FirstAidConfig.CLIENT.enableSounds.get()) {
            lastRushTicks = rushTicks;
            return;
        }

        RandomSource random = player.getRandom();
        boolean rushStarted = lastRushTicks <= 0 && rushTicks > 0;

        if (rushStarted || (onsetPlayedForSession == 0 && rushTicks >= 190)) {
            playOnset(client, random);
            onsetPlayedForSession = 1;
            nextStingerTicks = 8 + random.nextInt(10);
        }

        if (nextStingerTicks > 0) {
            --nextStingerTicks;
        } else {
            int layers = 1 + (random.nextFloat() < 0.45F ? 1 : 0);
            for (int i = 0; i < layers; i++) {
                playRandomRushSound(client, random, false);
            }
            nextStingerTicks = 12 + random.nextInt(17);
        }

        lastRushTicks = rushTicks;
    }

    public void clear() {
        lastRushTicks = 0;
        nextStingerTicks = 0;
        onsetPlayedForSession = 0;
    }

    private void playOnset(Minecraft client, RandomSource random) {
        playSound(client, Identifier.fromNamespaceAndPath(FirstAid.MODID, "debuff.heartbeat"), 0.48F, 1.05F + random.nextFloat() * 0.15F);
        playSound(client, Identifier.withDefaultNamespace("block.beacon.activate"), 0.42F, 0.85F + random.nextFloat() * 0.20F);
        if (random.nextBoolean()) {
            playSound(client, Identifier.fromNamespaceAndPath(FirstAid.MODID, "debuff.tinnitus"), 0.22F, 0.90F + random.nextFloat() * 0.25F);
        }
    }

    private void playRandomRushSound(Minecraft client, RandomSource random, boolean onset) {
        Identifier id = RUSH_SOUND_IDS.get(random.nextInt(RUSH_SOUND_IDS.size()));
        float volume = onset ? 0.40F : 0.15F + random.nextFloat() * 0.40F;
        float pitch = 0.75F + random.nextFloat() * 0.60F;
        if (id.getPath().contains("tinnitus")) {
            volume = Math.min(volume, 0.28F);
        }
        if (id.getPath().contains("levelup") || id.getPath().contains("experience_orb")) {
            volume = Math.min(volume, 0.22F);
            pitch = 0.70F + random.nextFloat() * 0.25F;
        }
        playSound(client, id, volume, pitch);
    }

    private static void playSound(Minecraft client, Identifier id, float volume, float pitch) {
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null) {
            return;
        }
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (event == null) {
            return;
        }
        volume = Mth.clamp(volume, 0.05F, 0.55F);
        pitch = Mth.clamp(pitch, 0.70F, 1.40F);
        level.playLocalSound(
                player.getX(),
                player.getY() + player.getEyeHeight() * 0.5D,
                player.getZ(),
                event,
                SoundSource.PLAYERS,
                volume,
                pitch,
                false
        );
    }
}
