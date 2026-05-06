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

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.api.medicine.ItemMedicine;
import ichttt.mods.firstaid.common.RegistryObjects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class HealingSoundController {
    private static @Nullable PillsUseSound activePillsSound;
    private static @Nullable ItemUseSound activeHealingSound;
    private static @Nullable ResourceLocation activeMedicineStartSound;
    private static @Nullable ItemStack activeMedicineStack;
    private static @Nullable ItemUseSound activeMedicineLoopSound;

    private HealingSoundController() {
    }

    public static void tick(Minecraft minecraft) {
        SoundManager soundManager = minecraft.getSoundManager();
        if (!FirstAidConfig.CLIENT.enableSounds.get()) {
            stopPillsSound(soundManager);
            stopMedicineSounds(soundManager);
            stopHealingSound(soundManager);
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player != null && player.isAlive() && isUsingPills(player)) {
            if (activePillsSound == null || activePillsSound.isStopped()) {
                activePillsSound = new PillsUseSound(player, RegistryObjects.PILLS_USE.get());
                soundManager.play(activePillsSound);
            }
        } else {
            stopPillsSound(soundManager);
        }

        if (player != null && player.isAlive()) {
            updateMedicineSounds(soundManager, player);
        } else {
            stopMedicineSounds(soundManager);
        }

        if (player != null && player.isAlive()) {
            ItemStack useStack = player.getUseItem();
            if (player.isUsingItem()
                    && useStack.getItem() instanceof ItemHealing itemHealing
                    && itemHealing.getApplySoundMode(useStack) == ItemHealing.ApplySoundMode.WHILE_USING
                    && itemHealing.getApplySoundEvent(useStack) != null) {
                if (activeHealingSound == null || activeHealingSound.isStopped() || !activeHealingSound.matches(player, useStack)) {
                    stopHealingSound(soundManager);
                    ItemStack playingStack = useStack.copy();
                    playingStack.setCount(1);
                    activeHealingSound = new ItemUseSound(player, playingStack, itemHealing.getApplySoundEvent(useStack));
                    soundManager.play(activeHealingSound);
                }
            } else {
                stopHealingSound(soundManager);
            }
        } else {
            stopHealingSound(soundManager);
        }
    }

    public static void clear() {
        stopPillsSound(Minecraft.getInstance().getSoundManager());
        stopMedicineSounds(Minecraft.getInstance().getSoundManager());
        stopHealingSound(Minecraft.getInstance().getSoundManager());
    }

    public static void playHealingApplySound(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!FirstAidConfig.CLIENT.enableSounds.get() || player == null || !(stack.getItem() instanceof ItemHealing itemHealing)) {
            return;
        }
        SoundEvent soundEvent = itemHealing.getApplySoundEvent(stack);
        if (soundEvent != null && itemHealing.getApplySoundMode(stack) == ItemHealing.ApplySoundMode.ON_COMPLETE) {
            player.playSound(soundEvent, 1.0F, 1.0F);
        }
    }

    public static void playRescueInteractionSound(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!FirstAidConfig.CLIENT.enableSounds.get() || player == null) {
            return;
        }

        SoundEvent soundEvent = stack.is(RegistryObjects.DEFIBRILLATOR.get())
                ? RegistryObjects.DEFIBRILLATOR_USE.get()
                : RegistryObjects.BANDAGE_USE.get();
        player.playSound(soundEvent, 1.0F, 1.0F);
    }

    private static void updateMedicineSounds(SoundManager soundManager, LocalPlayer player) {
        ItemStack useStack = player.getUseItem();
        if (player.isUsingItem() && useStack.getItem() instanceof ItemMedicine itemMedicine) {
            if (!matchesCurrentMedicine(player, useStack)) {
                stopMedicineSounds(soundManager);
                activeMedicineStack = useStack.copy();
                activeMedicineStack.setCount(1);
                SoundEvent startSound = itemMedicine.getUseStartSound(useStack);
                if (startSound != null) {
                    activeMedicineStartSound = startSound.getLocation();
                    player.playSound(startSound, 1.0F, 1.0F);
                }
            }

            SoundEvent loopSound = itemMedicine.getUseLoopSound(useStack);
            if (loopSound != null) {
                if (activeMedicineLoopSound == null || activeMedicineLoopSound.isStopped() || !activeMedicineLoopSound.matches(player, useStack)) {
                    stopMedicineLoopSound(soundManager);
                    ItemStack playingStack = useStack.copy();
                    playingStack.setCount(1);
                    activeMedicineLoopSound = new ItemUseSound(player, playingStack, loopSound);
                    soundManager.play(activeMedicineLoopSound);
                }
            } else {
                stopMedicineLoopSound(soundManager);
            }
        } else {
            stopMedicineSounds(soundManager);
        }
    }

    private static boolean matchesCurrentMedicine(LocalPlayer player, ItemStack stack) {
        return activeMedicineStack != null && player.isUsingItem() && ItemStack.isSameItemSameTags(activeMedicineStack, stack);
    }

    private static void stopMedicineSounds(SoundManager soundManager) {
        stopMedicineLoopSound(soundManager);
        if (activeMedicineStartSound != null) {
            soundManager.stop(activeMedicineStartSound, SoundSource.PLAYERS);
            activeMedicineStartSound = null;
        }
        activeMedicineStack = null;
    }

    private static void stopMedicineLoopSound(SoundManager soundManager) {
        if (activeMedicineLoopSound == null) {
            return;
        }
        activeMedicineLoopSound.stop();
        soundManager.stop(activeMedicineLoopSound);
        activeMedicineLoopSound = null;
    }

    private static void stopPillsSound(SoundManager soundManager) {
        if (activePillsSound == null) {
            return;
        }
        activePillsSound.stop();
        soundManager.stop(activePillsSound);
        activePillsSound = null;
    }

    private static void stopHealingSound(SoundManager soundManager) {
        if (activeHealingSound == null) {
            return;
        }
        activeHealingSound.stop();
        soundManager.stop(activeHealingSound);
        activeHealingSound = null;
    }

    private static boolean isUsingPills(LocalPlayer player) {
        return player.isUsingItem() && player.getUseItem().is(RegistryObjects.PAINKILLERS.get());
    }

    private static final class PillsUseSound implements TickableSoundInstance {
        private final LocalPlayer player;
        private final ResourceLocation location;
        private Sound sound = SoundManager.EMPTY_SOUND;
        private boolean stopped;

        private PillsUseSound(LocalPlayer player, SoundEvent event) {
            this.player = player;
            this.location = event.getLocation();
        }

        private void stop() {
            stopped = true;
        }

        @Override
        public boolean isStopped() {
            return stopped || !player.isAlive() || !isUsingPills(player);
        }

        @Override
        public void tick() {
            if (!player.isAlive() || !isUsingPills(player)) {
                stopped = true;
            }
        }

        @Nonnull
        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(@Nonnull SoundManager soundManager) {
            WeighedSoundEvents events = soundManager.getSoundEvent(location);
            if (events != null) {
                sound = events.getSound(SoundInstance.createUnseededRandom());
            }
            return events;
        }

        @Nonnull
        @Override
        public Sound getSound() {
            return sound;
        }

        @Nonnull
        @Override
        public SoundSource getSource() {
            return SoundSource.PLAYERS;
        }

        @Override
        public boolean isLooping() {
            return true;
        }

        @Override
        public boolean isRelative() {
            return false;
        }

        @Override
        public int getDelay() {
            return 0;
        }

        @Override
        public float getVolume() {
            return 0.85F;
        }

        @Override
        public float getPitch() {
            return 1.0F;
        }

        @Override
        public double getX() {
            return player.getX();
        }

        @Override
        public double getY() {
            return player.getY();
        }

        @Override
        public double getZ() {
            return player.getZ();
        }

        @Nonnull
        @Override
        public Attenuation getAttenuation() {
            return Attenuation.NONE;
        }
    }

    private static final class ItemUseSound implements TickableSoundInstance {
        private final LocalPlayer player;
        private final ItemStack stack;
        private final ResourceLocation location;
        private Sound sound = SoundManager.EMPTY_SOUND;
        private boolean stopped;

        private ItemUseSound(LocalPlayer player, ItemStack stack, SoundEvent event) {
            this.player = player;
            this.stack = stack;
            this.location = event.getLocation();
        }

        private void stop() {
            stopped = true;
        }

        private boolean matches(LocalPlayer player, ItemStack stack) {
            return this.player == player && ItemStack.isSameItemSameTags(this.stack, stack);
        }

        @Override
        public boolean isStopped() {
            return stopped || !player.isAlive() || !matchesCurrentUse();
        }

        @Override
        public void tick() {
            if (!matchesCurrentUse()) {
                stopped = true;
            }
        }

        private boolean matchesCurrentUse() {
            return player.isAlive() && player.isUsingItem() && ItemStack.isSameItemSameTags(this.stack, player.getUseItem());
        }

        @Nonnull
        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(@Nonnull SoundManager soundManager) {
            WeighedSoundEvents events = soundManager.getSoundEvent(location);
            if (events != null) {
                sound = events.getSound(SoundInstance.createUnseededRandom());
            }
            return events;
        }

        @Nonnull
        @Override
        public Sound getSound() {
            return sound;
        }

        @Nonnull
        @Override
        public SoundSource getSource() {
            return SoundSource.PLAYERS;
        }

        @Override
        public boolean isLooping() {
            return true;
        }

        @Override
        public boolean isRelative() {
            return false;
        }

        @Override
        public int getDelay() {
            return 0;
        }

        @Override
        public float getVolume() {
            return 0.85F;
        }

        @Override
        public float getPitch() {
            return 1.0F;
        }

        @Override
        public double getX() {
            return player.getX();
        }

        @Override
        public double getY() {
            return player.getY();
        }

        @Override
        public double getZ() {
            return player.getZ();
        }

        @Nonnull
        @Override
        public Attenuation getAttenuation() {
            return Attenuation.NONE;
        }
    }
}
