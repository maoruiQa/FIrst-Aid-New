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
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/** Personal tinnitus playback, shared by injury cues and morphine rush sounds. */
public final class TinnitusSoundController {
    private static final List<TinnitusSound> ACTIVE = new ArrayList<>();
    private static Level trackedLevel;
    private static int muteTicks;

    private TinnitusSoundController() {
    }

    public static SoundInstance play(SoundEvent event, float volume, float pitch) {
        return play(event, SoundSource.MASTER, volume, pitch, true, 0, 0, 0);
    }

    public static SoundInstance play(SoundEvent event, float volume, float pitch, int baseDurationTicks) {
        SoundInstance sound = play(event, volume, pitch);
        if (sound instanceof TinnitusSound tinnitus && baseDurationTicks > 0) {
            tinnitus.remainingTicks = Math.max(1, (int) Math.ceil(baseDurationTicks
                    * FirstAidConfig.CLIENT.tinnitusDurationPercent.get() / 100.0D));
        }
        return sound;
    }


    public static SoundInstance playAt(SoundEvent event, float volume, float pitch, double x, double y, double z) {
        return play(event, SoundSource.PLAYERS, volume, pitch, false, x, y, z);
    }

    private static SoundInstance play(SoundEvent event, SoundSource source, float volume, float pitch,
                                      boolean relative, double x, double y, double z) {
        Minecraft client = Minecraft.getInstance();
        if (!FirstAidConfig.CLIENT.tinnitusEnabled.get() || !FirstAidConfig.CLIENT.enableSounds.get()
                || muteTicks > 0 || client.player == null || client.level == null || !client.player.isAlive()) {
            return null;
        }
        if (CommonUtils.getDamageModel(client.player) instanceof PlayerDamageModel model && model.isAudioMuted()) {
            return null;
        }
        TinnitusSound sound = new TinnitusSound(event, source, volume, pitch, relative, x, y, z,
                FirstAidConfig.CLIENT.tinnitusDurationPercent.get());
        ACTIVE.add(sound);
        client.getSoundManager().play(sound);
        return sound;
    }

    public static void tick(Minecraft client) {
        if (muteTicks > 0) --muteTicks;
        if (trackedLevel != client.level || client.player == null || !client.player.isAlive() || !FirstAid.isSynced
                || !FirstAidConfig.CLIENT.tinnitusEnabled.get() || !FirstAidConfig.CLIENT.enableSounds.get()) {
            clear();
            trackedLevel = client.level;
            return;
        }
        if (muteTicks > 0 || (CommonUtils.getDamageModel(client.player) instanceof PlayerDamageModel model && model.isAudioMuted())) {
            clear();
            return;
        }
        ACTIVE.removeIf(sound -> {
            if (--sound.remainingTicks > 0) return false;
            client.getSoundManager().stop(sound);
            return true;
        });
    }

    public static void stop(SoundInstance sound) {
        if (sound == null) return;
        Minecraft.getInstance().getSoundManager().stop(sound);
        ACTIVE.remove(sound);
    }

    public static void clear() {
        for (TinnitusSound sound : ACTIVE) Minecraft.getInstance().getSoundManager().stop(sound);
        ACTIVE.clear();
    }

    public static void beginMute(int ticks) {
        clear();
        muteTicks = Math.max(muteTicks, Math.max(0, ticks));
    }

    static int durationTicks(float pitch, int percent) {
        if (percent < 1 || percent > 400) throw new IllegalArgumentException("Tinnitus duration must be 1-400 percent");
        // ponytail: bundled 10.589479s clip; read resource-pack metadata if custom clip lengths need support.
        float playbackPitch = Math.max(0.5F, Math.min(2.0F, pitch));
        return (int) Math.ceil(10.589479D * 20.0D / playbackPitch * percent / 100.0D);
    }

    private static final class TinnitusSound extends AbstractSoundInstance {
        private int remainingTicks;

        private TinnitusSound(SoundEvent event, SoundSource source, float volume, float pitch,
                              boolean relative, double x, double y, double z, int percent) {
            super(event, source, RandomSource.create());
            this.volume = volume;
            this.pitch = pitch;
            this.x = x;
            this.y = y;
            this.z = z;
            this.relative = relative;
            attenuation = relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR;
            looping = percent > 100;
            remainingTicks = durationTicks(pitch, percent);
        }
    }
}
