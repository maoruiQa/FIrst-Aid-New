package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAidConfig;
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
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class HealingSoundController {
    private static @Nullable PillsUseSound activePillsSound;

    private HealingSoundController() {
    }

    public static void tick(Minecraft minecraft) {
        if (!FirstAidConfig.CLIENT.enableSounds.get()) {
            stopPillsSound(minecraft.getSoundManager());
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isAlive() || !isUsingPills(player)) {
            stopPillsSound(minecraft.getSoundManager());
            return;
        }
        if (activePillsSound == null || activePillsSound.isStopped()) {
            activePillsSound = new PillsUseSound(player, RegistryObjects.PILLS_USE.value());
            minecraft.getSoundManager().play(activePillsSound);
        }
    }

    public static void clear() {
        stopPillsSound(Minecraft.getInstance().getSoundManager());
    }

    public static void playHealingApplySound() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!FirstAidConfig.CLIENT.enableSounds.get() || player == null) {
            return;
        }
        player.playSound(RegistryObjects.BANDAGE_USE.value(), 1.0F, 1.0F);
    }

    private static boolean isUsingPills(LocalPlayer player) {
        if (!player.isUsingItem()) {
            return false;
        }
        ItemStack stack = player.getUseItem();
        return stack.is(RegistryObjects.MORPHINE.get()) || stack.is(RegistryObjects.PAINKILLERS.get());
    }

    private static void stopPillsSound(SoundManager soundManager) {
        if (activePillsSound == null) {
            return;
        }
        activePillsSound.stop();
        soundManager.stop(activePillsSound);
        activePillsSound = null;
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
}
