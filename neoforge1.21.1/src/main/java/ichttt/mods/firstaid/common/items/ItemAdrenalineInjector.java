package ichttt.mods.firstaid.common.items;

import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

public class ItemAdrenalineInjector extends Item {

    private static boolean suppressNextSound = false;

    public ItemAdrenalineInjector(Item.Properties properties) {
        super(properties.stacksTo(1).durability(2));
    }

    @Override
    @Nonnull
    public ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level world, @Nonnull LivingEntity entityLiving) {
        if (entityLiving instanceof Player player) {
            if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel playerDamageModel) {
                playerDamageModel.applyAdrenalineInjection(player);
            } else {
                MobEffectInstance activePainkiller = player.getEffect(RegistryObjects.PAINKILLER_EFFECT);
                int duration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activePainkiller == null ? 0 : activePainkiller.getDuration());
                MobEffectInstance activeAbsorption = player.getEffect(MobEffects.ABSORPTION);
                int absorptionDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeAbsorption == null ? 0 : activeAbsorption.getDuration());
                int absorptionAmplifier = Math.max(1, activeAbsorption == null ? 0 : activeAbsorption.getAmplifier());
                MobEffectInstance activeHaste = player.getEffect(MobEffects.DIG_SPEED);
                MobEffectInstance activeStrength = player.getEffect(MobEffects.DAMAGE_BOOST);
                MobEffectInstance activeSpeed = player.getEffect(MobEffects.MOVEMENT_SPEED);
                int hasteDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeHaste == null ? 0 : activeHaste.getDuration());
                int hasteAmplifier = Math.max(0, activeHaste == null ? 0 : activeHaste.getAmplifier());
                int strengthDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeStrength == null ? 0 : activeStrength.getDuration());
                int strengthAmplifier = Math.max(0, activeStrength == null ? 0 : activeStrength.getAmplifier());
                int speedDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeSpeed == null ? 0 : activeSpeed.getDuration());
                int speedAmplifier = Math.max(0, activeSpeed == null ? 0 : activeSpeed.getAmplifier());
                player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, duration, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, absorptionDuration, absorptionAmplifier, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, hasteDuration, hasteAmplifier, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, strengthDuration, strengthAmplifier, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedDuration, speedAmplifier, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0, false, false));
            }
            if (!player.getAbilities().instabuild) {
                EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, player, slot);
            }
        }
        if (world.isClientSide()) {
            suppressNextSound = true;
        }
        return stack;
    }

    @Override
    @Nonnull
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(Level world, Player player, @Nonnull InteractionHand hand) {
        if (world.isClientSide()) {
            if (suppressNextSound) {
                suppressNextSound = false;
            } else {
                player.playSound(RegistryObjects.ADRENALINE_INJECTOR_USE.value(), 1.0F, 1.0F);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), world.isClientSide());
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 40;
    }

    @Override
    public void releaseUsing(@Nonnull ItemStack stack, @Nonnull Level world, @Nonnull LivingEntity entityLiving, int timeCharged) {
        if (world.isClientSide()) {
            Minecraft.getInstance().getSoundManager().stop(
                    RegistryObjects.ADRENALINE_INJECTOR_USE.value().getLocation(), SoundSource.PLAYERS);
        }
    }
}
