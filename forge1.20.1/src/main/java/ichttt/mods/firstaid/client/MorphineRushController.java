/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * First ~10 seconds after morphine activates: layered random "rush" stingers.
 */
public final class MorphineRushController {
    private static final List<ResourceLocation> RUSH_SOUND_IDS = List.of(
            new ResourceLocation(FirstAid.MODID, "debuff.heartbeat"),
            new ResourceLocation(FirstAid.MODID, "debuff.tinnitus"),
            new ResourceLocation("minecraft", "block.beacon.activate"),
            new ResourceLocation("minecraft", "block.beacon.ambient"),
            new ResourceLocation("minecraft", "block.beacon.power_select"),
            new ResourceLocation("minecraft", "block.respawn_anchor.charge"),
            new ResourceLocation("minecraft", "entity.player.levelup"),
            new ResourceLocation("minecraft", "block.enchantment_table.use"),
            new ResourceLocation("minecraft", "block.amethyst_block.chime"),
            new ResourceLocation("minecraft", "entity.experience_orb.pickup"),
            new ResourceLocation("minecraft", "entity.allay.ambient_with_item")
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

        RandomSource random = level.random;
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
        playSound(client, new ResourceLocation(FirstAid.MODID, "debuff.heartbeat"), 0.48F, 1.05F + random.nextFloat() * 0.15F);
        playSound(client, new ResourceLocation("minecraft", "block.beacon.activate"), 0.42F, 0.85F + random.nextFloat() * 0.20F);
        if (random.nextBoolean()) {
            playSound(client, new ResourceLocation(FirstAid.MODID, "debuff.tinnitus"), 0.22F, 0.90F + random.nextFloat() * 0.25F);
        }
    }

    private void playRandomRushSound(Minecraft client, RandomSource random, boolean onset) {
        ResourceLocation id = RUSH_SOUND_IDS.get(random.nextInt(RUSH_SOUND_IDS.size()));
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

    private static void playSound(Minecraft client, ResourceLocation id, float volume, float pitch) {
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null) {
            return;
        }
        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(id);
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
