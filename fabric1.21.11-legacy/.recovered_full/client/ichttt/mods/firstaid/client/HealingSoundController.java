/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAidConfig
 *  ichttt.mods.firstaid.common.RegistryObjects
 *  javax.annotation.Nonnull
 *  javax.annotation.Nullable
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.resources.sounds.Sound
 *  net.minecraft.client.resources.sounds.SoundInstance
 *  net.minecraft.client.resources.sounds.SoundInstance$Attenuation
 *  net.minecraft.client.resources.sounds.TickableSoundInstance
 *  net.minecraft.client.sounds.SoundManager
 *  net.minecraft.client.sounds.WeighedSoundEvents
 *  net.minecraft.resources.Identifier
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 */
package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.RegistryObjects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class HealingSoundController {
    @Nullable
    private static PillsUseSound activePillsSound;

    private HealingSoundController() {
    }

    public static void tick(Minecraft minecraft) {
        if (!((Boolean)FirstAidConfig.CLIENT.enableSounds.get()).booleanValue()) {
            HealingSoundController.stopPillsSound(minecraft.getSoundManager());
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isAlive() || !HealingSoundController.isUsingPills(player)) {
            HealingSoundController.stopPillsSound(minecraft.getSoundManager());
            return;
        }
        if (activePillsSound == null || activePillsSound.isStopped()) {
            activePillsSound = new PillsUseSound(player, (SoundEvent)RegistryObjects.PILLS_USE.value());
            minecraft.getSoundManager().play((SoundInstance)activePillsSound);
        }
    }

    public static void clear() {
        HealingSoundController.stopPillsSound(Minecraft.getInstance().getSoundManager());
    }

    public static void playHealingApplySound() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!((Boolean)FirstAidConfig.CLIENT.enableSounds.get()).booleanValue() || player == null) {
            return;
        }
        player.playSound((SoundEvent)RegistryObjects.BANDAGE_USE.value(), 1.0f, 1.0f);
    }

    private static boolean isUsingPills(LocalPlayer player) {
        if (!player.isUsingItem()) {
            return false;
        }
        ItemStack stack = player.getUseItem();
        return stack.is((Item)RegistryObjects.MORPHINE.get()) || stack.is((Item)RegistryObjects.PAINKILLERS.get());
    }

    private static void stopPillsSound(SoundManager soundManager) {
        if (activePillsSound == null) {
            return;
        }
        activePillsSound.stop();
        soundManager.stop((SoundInstance)activePillsSound);
        activePillsSound = null;
    }

    private static final class PillsUseSound
    implements TickableSoundInstance {
        private final LocalPlayer player;
        private final Identifier location;
        private Sound sound = SoundManager.EMPTY_SOUND;
        private boolean stopped;

        private PillsUseSound(LocalPlayer player, SoundEvent event) {
            this.player = player;
            this.location = event.location();
        }

        private void stop() {
            this.stopped = true;
        }

        public boolean isStopped() {
            return this.stopped || !this.player.isAlive() || !HealingSoundController.isUsingPills(this.player);
        }

        public void tick() {
            if (!this.player.isAlive() || !HealingSoundController.isUsingPills(this.player)) {
                this.stopped = true;
            }
        }

        @Nonnull
        public Identifier getIdentifier() {
            return this.location;
        }

        @Nullable
        public WeighedSoundEvents resolve(@Nonnull SoundManager soundManager) {
            WeighedSoundEvents events = soundManager.getSoundEvent(this.location);
            if (events != null) {
                this.sound = events.getSound(SoundInstance.createUnseededRandom());
            }
            return events;
        }

        @Nonnull
        public Sound getSound() {
            return this.sound;
        }

        @Nonnull
        public SoundSource getSource() {
            return SoundSource.PLAYERS;
        }

        public boolean isLooping() {
            return true;
        }

        public boolean isRelative() {
            return false;
        }

        public int getDelay() {
            return 0;
        }

        public float getVolume() {
            return 0.85f;
        }

        public float getPitch() {
            return 1.0f;
        }

        public double getX() {
            return this.player.getX();
        }

        public double getY() {
            return this.player.getY();
        }

        public double getZ() {
            return this.player.getZ();
        }

        @Nonnull
        public SoundInstance.Attenuation getAttenuation() {
            return SoundInstance.Attenuation.NONE;
        }
    }
}

