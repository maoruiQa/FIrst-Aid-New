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

package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.init.FirstAidDataAttachments;
import ichttt.mods.firstaid.common.registries.FirstAidRegistryLookups;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.PlayerSizeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.UUID;

public class EventHandler {
    public static final Random RAND = new Random();
    private static final EntityDimensions PLAYER_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(1.4F, 1.0F);
    private static final int RESCUE_DURATION_TICKS = PlayerDamageModel.getRescueDurationTicks();
    private static final int DEFIBRILLATOR_RESCUE_DURATION_TICKS = PlayerDamageModel.getDefibrillatorRescueDurationTicks();
    private static final int EXECUTION_DURATION_TICKS = PlayerDamageModel.getExecutionDurationTicks();

    public static final Map<Player, ProjectileHitContext> hitList = new WeakHashMap<>();
    private static final Map<UUID, RescueProgress> rescueProgress = new HashMap<>();
    private static final Map<UUID, ExecutionProgress> executionProgress = new HashMap<>();
    private static final IDamageDistributionAlgorithm FOOT_ONLY_DAMAGE_DISTRIBUTION = new StandardDamageDistributionAlgorithm(
            Collections.singletonMap(EquipmentSlot.FEET, CommonUtils.getPartListForSlot(EquipmentSlot.FEET)),
            false,
            true
    );

    private static void awardStarterRecipes(ServerPlayer player) {
        List<RecipeHolder<?>> recipes = Objects.requireNonNull(player.level().getServer())
                .getRecipeManager()
                .getRecipes()
                .stream()
                .filter(recipe -> FirstAid.MODID.equals(recipe.id().getNamespace()))
                .toList();
        if (!recipes.isEmpty()) {
            player.awardRecipes(recipes);
        }
    }

    public static Boolean preHandleCustomPlayerDamage(Player player, DamageSource source, float amountToDamage) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return null;
        if (isProtectedUnconsciousSuffocation(damageModel, source)) {
            hitList.remove(player);
            return Boolean.FALSE;
        }

        if (amountToDamage == Float.MAX_VALUE || Float.isNaN(amountToDamage) || amountToDamage == Float.POSITIVE_INFINITY) {
            damageModel.forEach(damageablePart -> damageablePart.currentHealth = 0F);
            if (player instanceof ServerPlayer serverPlayer)
                CommonUtils.syncDamageModel(serverPlayer);
            CommonUtils.killPlayer(damageModel, player, source);
            hitList.remove(player);
            return Boolean.TRUE;
        }
        return null;
    }

    public static boolean handleCustomPlayerDamage(Player player, DamageSource source, float amountToDamage) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return false;
        boolean addStat = amountToDamage < 3.4028235E37F;
        IDamageDistributionAlgorithm damageDistribution = getForcedDamageDistribution(source);
        if (damageDistribution == null) {
            damageDistribution = FirstAidRegistryLookups.getDamageDistributions(source.type());
        }

        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            Entity directEntity = source.getDirectEntity();
            ProjectileHitContext projectileHitContext = hitList.remove(player);
            if (projectileHitContext != null && projectileHitContext.projectile() == directEntity) {
                IDamageDistributionAlgorithm projectileDistribution = PlayerSizeHelper.getProjectileDistribution(player, projectileHitContext.hitPosition());
                if (projectileDistribution != null) {
                    damageDistribution = projectileDistribution;
                }
            }

            if (damageDistribution == null && directEntity != null) {
                EquipmentSlot slot = PlayerSizeHelper.getSlotTypeForProjectileHit(directEntity, player);
                if (slot != null) {
                    damageDistribution = new StandardDamageDistributionAlgorithm(Collections.singletonMap(slot, CommonUtils.getPartListForSlot(slot)), false, true);
                }
            }
        }
        if (damageDistribution == null) {
            // No given distribution found, and no projectile distribution either. Let's check if we can tell by the source where we should apply the damage, otherwise fall back to random
            damageDistribution = PlayerSizeHelper.getMeleeDistribution(player, source);
            if (damageDistribution == null) {
                damageDistribution = RandomDamageDistributionAlgorithm.getDefault();
            }
        }

        IDamageDistributionAlgorithm finalDamageDistribution = damageDistribution;
        float finalAmountToDamage = amountToDamage;
        boolean redistributeLeftoverDamage = shouldRedistributeLeftoverDamage(source);
        CommonUtils.runWithoutSetHealthInterception(
                () -> DamageDistribution.handleDamageTaken(finalDamageDistribution, damageModel, finalAmountToDamage, player, source, addStat, redistributeLeftoverDamage));
        hitList.remove(player);
        return true;
    }

    public static IDamageDistributionAlgorithm getForcedDamageDistribution(DamageSource source) {
        return CommonUtils.isFootOnlyDamageSource(source) ? FOOT_ONLY_DAMAGE_DISTRIBUTION : null;
    }

    private static boolean shouldRedistributeLeftoverDamage(DamageSource source) {
        return !CommonUtils.isFootOnlyDamageSource(source);
    }

    @SubscribeEvent(priority =  EventPriority.LOWEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        HitResult result = event.getRayTraceResult();
        if (result.getType() != HitResult.Type.ENTITY)
            return;

        Entity entity = ((EntityHitResult) result).getEntity();
        if (!entity.level().isClientSide() && entity instanceof Player) {
            hitList.put((Player) entity, new ProjectileHitContext(event.getEntity(), result.getLocation()));
        }
    }

    private record ProjectileHitContext(Entity projectile, Vec3 hitPosition) {
    }

    @SubscribeEvent
    public static void tickPlayers(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.getAbilities().invulnerable) {
            if (!player.isAlive()) return;
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            if (!player.level().isClientSide() && damageModel instanceof PlayerDamageModel playerDamageModel) {
                float nearMissStrength = getNearbyProjectileStrength(player);
                if (nearMissStrength > 0.0F) {
                    playerDamageModel.registerAdrenalineNearMiss(player, nearMissStrength);
                }
                if (playerDamageModel.isUnconscious()) {
                    clearAttackTargetsAround(player, 24.0D);
                }
            }
            damageModel.tick(player.level(), player);
            if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                tickRescueProgress(serverPlayer);
                tickExecutionProgress(serverPlayer);
            }
            hitList.remove(player);
        }
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (ModList.get().isLoaded("morpheus")) return;
        for (Player player : event.getLevel().players()) {
            if (player.isSleepingLongEnough()) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) return;
                damageModel.sleepHeal(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation tableName = event.getName();
        int bandage, plaster, morphine;
        NumberProvider bandageMax  = UniformGenerator.between(1, 3);
        NumberProvider plasterMax  = UniformGenerator.between(1, 5);
        NumberProvider morphineMax = UniformGenerator.between(1, 2);
        NumberProvider poolRolls   = ConstantValue.exactly(1.0F);
        if (tableName.equals(BuiltInLootTables.SPAWN_BONUS_CHEST)) {
            bandage = 8;
            plaster = 16;
            morphine = 4;
            morphineMax = ConstantValue.exactly(1);
        } else if (tableName.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR) || tableName.equals(BuiltInLootTables.STRONGHOLD_CROSSING) || tableName.equals(BuiltInLootTables.ABANDONED_MINESHAFT)) {
            bandage = 20;
            plaster = 24;
            morphine = 8;
            poolRolls = UniformGenerator.between(0, 1);
        } else if (tableName.equals(BuiltInLootTables.VILLAGE_BUTCHER)) {
            bandage = 4;
            plaster = 20;
            morphine = 2;
            plasterMax = UniformGenerator.between(3, 8);
        } else if (tableName.equals(BuiltInLootTables.IGLOO_CHEST)) {
            bandage = 4;
            plaster = 8;
            morphine = 2;
            poolRolls = UniformGenerator.between(0, 1);
        } else if (tableName.equals(BuiltInLootTables.SHIPWRECK_SUPPLY)) {
            bandage = 4;
            plaster = 8;
            morphine = 2;
            bandageMax = UniformGenerator.between(1, 2);
            plasterMax = UniformGenerator.between(1, 3);
            morphineMax = ConstantValue.exactly(1);
            poolRolls = UniformGenerator.between(0, 1);
        } else {
            return;
        }
        LootPool.Builder builder = LootPool.lootPool().name("firstaid_main").setRolls(poolRolls);
        builder.add(LootItem.lootTableItem(RegistryObjects.BANDAGE::get)
                    .apply(SetItemCountFunction.setCount(bandageMax))
                    .setWeight(bandage)
                    .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.PLASTER::get)
                    .apply(SetItemCountFunction.setCount(plasterMax))
                    .setWeight(plaster)
                    .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.MORPHINE::get)
                    .apply(SetItemCountFunction.setCount(morphineMax))
                    .setWeight(morphine)
                    .setQuality(0));
        event.getTable().addPool(builder.build());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.isDeadOrDying() || !CommonUtils.hasDamageModel(entity))
            return;
        event.setCanceled(true);
        if (entity.level().isClientSide() || !FirstAidConfig.SERVER.allowOtherHealingItems.get())
            return;
        float amount = event.getAmount();
        //Hacky shit to reduce vanilla regen
        boolean fromFood = Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(stackTraceElement -> stackTraceElement.getClassName().equals(FoodData.class.getName()));
        if (fromFood) {
            amount = amount * (float) (double) FirstAidConfig.SERVER.naturalRegenMultiplier.get();
        } else {
            amount = amount * (float) (double) FirstAidConfig.SERVER.otherRegenMultiplier.get();
        }
        if (FirstAidConfig.GENERAL.debug.get()) {
            CommonUtils.debugLogStacktrace("External healing: : " + amount);
        }
        if (fromFood) {
            HealthDistribution.applyNaturalRegen(amount, (Player) entity, true);
        } else {
            HealthDistribution.distributeHealth(amount, (Player) entity, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            FirstAid.LOGGER.debug("Sending damage model to {}", event.getEntity().getName());
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(event.getEntity());
            if (damageModel == null) return;
            if (damageModel.hasTutorial)
                CapProvider.tutorialDone.add(event.getEntity().getName().getString());
            ServerPlayer playerMP = (ServerPlayer) event.getEntity();
            awardStarterRecipes(playerMP);
            CommonUtils.syncDamageModel(playerMP);
            sendOpCommandTip(playerMP);
        }
    }

    @SubscribeEvent(priority =  EventPriority.LOW)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        hitList.remove(event.getEntity());
        rescueProgress.remove(event.getEntity().getUUID());
        executionProgress.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        LevelAccessor world = event.getLevel();
        if (!world.isClientSide() && world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION)
                    .set(FirstAid.naturalRegenMode != FirstAid.NaturalRegenMode.OFF, serverLevel.getServer());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && player instanceof ServerPlayer) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            CommonUtils.syncDamageModel((ServerPlayer) player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Player rescuer = event.getEntity();
        if (isUnconscious(rescuer)) {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        cancelIfUnconscious(event);
    }

    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        cancelIfUnconscious(event);
    }

    @SubscribeEvent
    public static void onBlockAttack(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfUnconscious(event);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelIfUnconscious(event);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void adjustHitboxSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player && !entity.isPassenger()) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getExistingDamageModel(player);
            if (damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious()) {
                event.setNewSize(PlayerDamageModel.getUnconsciousDimensions(playerDamageModel.shouldUseCrampedUnconsciousDimensions(player)));
            }
        }
    }

    @SubscribeEvent
    public static void onSetAttackTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget instanceof Player player && isUnconscious(player)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.shouldUpdateStaticData()) {
            FirstAidRegistryLookups.init(event.getRegistryAccess(), event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        FirstAidConfig.applyCommandSettings();
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppedEvent event) {
        FirstAid.LOGGER.debug("Cleaning up");
        FirstAid.dynamicPainEnabled = true;
        FirstAid.lowSuppressionEnabled = false;
        FirstAid.rescueWakeUpEnabled = false;
        FirstAid.rescueWakeUpDelaySeconds = FirstAid.DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS;
        FirstAid.naturalRegenMode = FirstAid.NaturalRegenMode.LIMITED;
        FirstAid.naturalRegenStrategy = FirstAid.NaturalRegenStrategy.CRITICAL;
        FirstAid.medicineEffectMode = FirstAid.MedicineEffectMode.REALISTIC;
        FirstAid.injuryDebuffMode = FirstAid.InjuryDebuffMode.NORMAL;
        FirstAid.injuryDebuffOverrides.clear();
        CapProvider.tutorialDone.clear();
        EventHandler.hitList.clear();
        rescueProgress.clear();
        executionProgress.clear();
        FirstAidRegistryLookups.reset();
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        DebugDamageCommand.register(event.getDispatcher());
        FirstAidCommand.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (!event.isEndConquered() && !player.level().isClientSide() && player instanceof ServerPlayer) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            damageModel.runScaleLogic(player);
            damageModel.forEach(damageablePart -> damageablePart.heal(damageablePart.getMaxHealth(), player, false));
            if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                playerDamageModel.clearStatusEffects();
            }
            damageModel.scheduleResync();
        }
    }

    private static float getNearbyProjectileStrength(Player player) {
        AABB scanBox = player.getBoundingBox().inflate(3.25D);
        AABB playerBox = player.getBoundingBox().inflate(0.12D);
        Vec3 eyePosition = player.getEyePosition();
        Vec3 torsoPosition = player.position().add(0.0D, player.getBbHeight() * 0.6D, 0.0D);
        float strongest = 0.0F;
        List<Projectile> projectiles = player.level().getEntitiesOfClass(Projectile.class, scanBox, projectile -> {
            if (!projectile.isAlive() || projectile.getOwner() == player) {
                return false;
            }
            if (projectile instanceof ThrownPotion) {
                return false;
            }
            return projectile.getDeltaMovement().lengthSqr() >= 0.02D;
        });
        for (Projectile projectile : projectiles) {
            Vec3 currentPosition = projectile.position();
            Vec3 previousPosition = currentPosition.subtract(projectile.getDeltaMovement());
            Vec3 endPosition = currentPosition.add(projectile.getDeltaMovement());
            if (playerBox.intersects(projectile.getBoundingBox()) || playerBox.clip(previousPosition, endPosition).isPresent()) {
                continue;
            }
            strongest = Math.max(strongest, getNearMissStrength(player, projectile, previousPosition, endPosition, eyePosition));
            strongest = Math.max(strongest, getNearMissStrength(player, projectile, previousPosition, endPosition, torsoPosition));
        }
        return strongest;
    }

    private static float getNearMissStrength(Player player, Projectile projectile, Vec3 start, Vec3 end, Vec3 target) {
        ClosestPointResult closestPointResult = closestPointOnSegment(start, end, target);
        if (closestPointResult.progress <= 0.0D || closestPointResult.progress >= 1.0D) {
            return 0.0F;
        }
        double distance = closestPointResult.point.distanceTo(target);
        if (distance > 1.85D) {
            return 0.0F;
        }
        BlockHitResult hitResult = player.level().clip(new ClipContext(closestPointResult.point, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hitResult.getType() != HitResult.Type.MISS) {
            return 0.0F;
        }
        double speed = projectile.getDeltaMovement().length();
        float speedFactor = Mth.clamp((float) ((speed - 0.18D) / 0.65D), 0.0F, 1.0F);
        float distanceFactor = Mth.clamp(1.32F - (float) (distance / 1.85D), 0.0F, 1.0F);
        return Mth.clamp(distanceFactor * (0.82F + 0.58F * speedFactor), 0.0F, 1.45F);
    }

    private static ClosestPointResult closestPointOnSegment(Vec3 start, Vec3 end, Vec3 target) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-7D) {
            return new ClosestPointResult(start, 0.0D);
        }
        double progress = Mth.clamp(target.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return new ClosestPointResult(start.add(segment.scale(progress)), progress);
    }

    private static <T extends PlayerInteractEvent & ICancellableEvent> void cancelIfUnconscious(T event) {
        if (isUnconscious(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isUnconscious(Player player) {
        return isUnconscious(player, true);
    }

    private static boolean isUnconscious(Player player, boolean allowCreate) {
        AbstractPlayerDamageModel damageModel = allowCreate
                ? CommonUtils.getDamageModel(player)
                : CommonUtils.getExistingDamageModel(player);
        return damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious();
    }

    private static boolean isProtectedUnconsciousSuffocation(AbstractPlayerDamageModel damageModel, DamageSource source) {
        return damageModel instanceof PlayerDamageModel playerDamageModel
                && playerDamageModel.isUnconscious()
                && source.is(DamageTypes.IN_WALL);
    }

    private static void clearAttackTargetsAround(LivingEntity victim, double range) {
        LevelAccessor level = victim.level();
        if (!(level instanceof net.minecraft.world.level.Level gameLevel)) {
            return;
        }
        List<Mob> mobs = gameLevel.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(range));
        for (Mob mob : mobs) {
            if (mob.getTarget() == victim) {
                mob.setTarget(null);
                Brain<?> brain = mob.getBrain();
                eraseMemory(brain, MemoryModuleType.ANGRY_AT);
                eraseMemory(brain, MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    private static void eraseMemory(Brain<?> brain, MemoryModuleType<?> type) {
        if (brain.hasMemoryValue(type)) {
            brain.eraseMemory(type);
        }
    }

    private static void tickRescueProgress(ServerPlayer rescuer) {
        InteractionTarget rescueTarget = findRescueTarget(rescuer, true);
        if (rescueTarget == null) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }

        ItemStack rescueStack = rescuer.getItemInHand(rescueTarget.hand());
        int rescueDurationTicks = getRescueDurationTicks(rescueStack);
        RescueProgress progress = rescueProgress.get(rescuer.getUUID());
        if (progress == null || !progress.matches(rescueTarget, rescueDurationTicks)) {
            progress = new RescueProgress(rescueTarget.target().getUUID(), rescueTarget.hand(), 0, rescueDurationTicks);
        }

        int nextTicks = Math.min(rescueDurationTicks, progress.ticks() + 1);
        if (nextTicks < rescueDurationTicks) {
            rescueProgress.put(rescuer.getUUID(), progress.withTicks(nextTicks));
            return;
        }

        completeRescue(rescuer, rescueTarget);
    }

    private static void tickExecutionProgress(ServerPlayer executor) {
        InteractionTarget executionTarget = findExecutionTarget(executor, true);
        if (executionTarget == null) {
            executionProgress.remove(executor.getUUID());
            return;
        }

        ExecutionProgress progress = executionProgress.get(executor.getUUID());
        if (progress == null || !progress.matches(executionTarget)) {
            progress = new ExecutionProgress(executionTarget.target().getUUID(), executionTarget.hand(), 0);
        }

        int nextTicks = Math.min(EXECUTION_DURATION_TICKS, progress.ticks() + 1);
        if (nextTicks < EXECUTION_DURATION_TICKS) {
            executionProgress.put(executor.getUUID(), progress.withTicks(nextTicks));
            return;
        }

        completeExecution(executor, executionTarget);
    }

    public static void attemptImmediateRescue(ServerPlayer rescuer) {
        InteractionTarget rescueTarget = findRescueTarget(rescuer, true);
        if (rescueTarget == null) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }
        completeRescue(rescuer, rescueTarget);
    }

    public static void attemptImmediateExecution(ServerPlayer executor) {
        InteractionTarget executionTarget = findExecutionTarget(executor, true);
        if (executionTarget == null) {
            executionProgress.remove(executor.getUUID());
            return;
        }
        completeExecution(executor, executionTarget);
    }

    private static void completeRescue(ServerPlayer rescuer, InteractionTarget rescueTarget) {
        ItemStack stack = rescuer.getItemInHand(rescueTarget.hand());
        if (!isRescueItem(stack)) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(rescueTarget.target());
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }

        boolean usingDefibrillator = isDefibrillator(stack);
        if (usingDefibrillator) {
            stack.hurtAndBreak(1, rescuer, getEquipmentSlot(rescueTarget.hand()));
        } else {
            stack.shrink(1);
        }

        boolean rescued = usingDefibrillator
                ? playerDamageModel.defibrillatorRescueFromCriticalState(rescueTarget.target(), FirstAid.rescueWakeUpEnabled)
                : playerDamageModel.rescueFromCriticalState(rescueTarget.target(), null, FirstAid.rescueWakeUpEnabled);
        if (rescued) {
            rescuer.displayClientMessage(Component.translatable("firstaid.gui.rescue_other", rescueTarget.target().getDisplayName()).withStyle(ChatFormatting.GREEN), true);
            rescueTarget.target().displayClientMessage(Component.translatable("firstaid.gui.rescue_received", rescuer.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        }
        rescueProgress.remove(rescuer.getUUID());
    }

    private static void completeExecution(ServerPlayer executor, InteractionTarget executionTarget) {
        ItemStack stack = executor.getItemInHand(executionTarget.hand());
        if (!CommonUtils.isExecutionItem(stack)) {
            executionProgress.remove(executor.getUUID());
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(executionTarget.target());
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
            executionProgress.remove(executor.getUUID());
            return;
        }

        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(1, executor, getEquipmentSlot(executionTarget.hand()));
        }
        executor.displayClientMessage(Component.translatable("firstaid.gui.execute_other", executionTarget.target().getDisplayName()).withStyle(ChatFormatting.RED), true);
        executionTarget.target().displayClientMessage(Component.translatable("firstaid.gui.execute_received", executor.getDisplayName()).withStyle(ChatFormatting.RED), true);
        playerDamageModel.giveUp(executionTarget.target());
        executionProgress.remove(executor.getUUID());
    }

    private static InteractionTarget findRescueTarget(Player rescuer, boolean requireSneaking) {
        if (rescuer == null || rescuer.level().isClientSide() || isUnconscious(rescuer)) {
            return null;
        }
        if (requireSneaking && !rescuer.isCrouching()) {
            return null;
        }

        InteractionSelection selection = getInteractionSelection(rescuer);
        if (selection == null || selection.type() != InteractionType.RESCUE) {
            return null;
        }

        Player closestTarget = findClosestRescueTarget(rescuer);
        return closestTarget == null ? null : new InteractionTarget(closestTarget, selection.hand());
    }

    private static InteractionTarget findExecutionTarget(Player executor, boolean requireSneaking) {
        if (executor == null || executor.level().isClientSide() || isUnconscious(executor)) {
            return null;
        }
        if (requireSneaking && !executor.isCrouching()) {
            return null;
        }

        InteractionSelection selection = getInteractionSelection(executor);
        if (selection == null || selection.type() != InteractionType.EXECUTE) {
            return null;
        }

        Player closestTarget = findClosestRescueTarget(executor);
        return closestTarget == null ? null : new InteractionTarget(closestTarget, selection.hand());
    }

    private static Player findClosestRescueTarget(Player actor) {
        double maxDistanceSqr = PlayerDamageModel.getRescueRange() * PlayerDamageModel.getRescueRange();
        Player closestTarget = null;
        double closestDistanceSqr = maxDistanceSqr;
        for (Player candidate : actor.level().players()) {
            if (candidate == actor || !candidate.isAlive()) {
                continue;
            }
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(candidate);
            if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
                continue;
            }
            double distanceSqr = actor.distanceToSqr(candidate);
            if (distanceSqr > closestDistanceSqr) {
                continue;
            }
            closestDistanceSqr = distanceSqr;
            closestTarget = candidate;
        }
        return closestTarget;
    }

    private static InteractionSelection getInteractionSelection(Player player) {
        if (isRescueItem(player.getMainHandItem())) {
            return new InteractionSelection(InteractionType.RESCUE, InteractionHand.MAIN_HAND);
        }
        if (CommonUtils.isExecutionItem(player.getMainHandItem())) {
            return new InteractionSelection(InteractionType.EXECUTE, InteractionHand.MAIN_HAND);
        }
        if (isRescueItem(player.getOffhandItem())) {
            return new InteractionSelection(InteractionType.RESCUE, InteractionHand.OFF_HAND);
        }
        if (CommonUtils.isExecutionItem(player.getOffhandItem())) {
            return new InteractionSelection(InteractionType.EXECUTE, InteractionHand.OFF_HAND);
        }
        return null;
    }

    private static boolean isRescueItem(ItemStack stack) {
        return stack.is(RegistryObjects.BANDAGE.get()) || stack.is(RegistryObjects.PLASTER.get()) || isDefibrillator(stack);
    }

    private static boolean isDefibrillator(ItemStack stack) {
        return stack.is(RegistryObjects.DEFIBRILLATOR.get());
    }

    private static int getRescueDurationTicks(ItemStack stack) {
        return isDefibrillator(stack) ? DEFIBRILLATOR_RESCUE_DURATION_TICKS : RESCUE_DURATION_TICKS;
    }

    private static EquipmentSlot getEquipmentSlot(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    private static void sendOpCommandTip(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            return;
        }
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.subheader").withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(buildCommandTipLine(
                "firstaid.tip.commands.group.core",
                buildCommandTipChip("firstaid.tip.commands.pain.label", "firstaid.tip.commands.pain.detail", "/firstaid pain dynamic", ChatFormatting.AQUA),
                buildCommandTipChip("firstaid.tip.commands.suppression.label", "firstaid.tip.commands.suppression.detail", "/firstaid suppression dynamic", ChatFormatting.AQUA),
                buildCommandTipChip("firstaid.tip.commands.medicineeffect.label", "firstaid.tip.commands.medicineeffect.detail", "/firstaid medicineeffect assisted", ChatFormatting.YELLOW)
        ), false);
        player.displayClientMessage(buildCommandTipLine(
                "firstaid.tip.commands.group.rescue",
                buildCommandTipChip("firstaid.tip.commands.naturalregen.label", "firstaid.tip.commands.naturalregen.detail", "/firstaid naturalregen limited critical", ChatFormatting.GREEN),
                buildCommandTipChip("firstaid.tip.commands.revivewakeup.label", "firstaid.tip.commands.revivewakeup.detail", "/firstaid revivewakeup on 15", ChatFormatting.GREEN)
        ), false);
        player.displayClientMessage(buildCommandTipLine(
                "firstaid.tip.commands.group.advanced",
                buildCommandTipChip("firstaid.tip.commands.injurydebuff.label", "firstaid.tip.commands.injurydebuff.detail", "/firstaid injurydebuff normal", ChatFormatting.GOLD),
                buildCommandTipChip("firstaid.tip.commands.damagepart.label", "firstaid.tip.commands.damagepart.detail", "/damagePart HEAD 4", ChatFormatting.RED)
        ), false);
    }

    private static Component buildCommandTipLine(String sectionKey, MutableComponent... commandChips) {
        MutableComponent line = Component.translatable(sectionKey).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD);
        for (MutableComponent commandChip : commandChips) {
            line.append(Component.literal(" "));
            line.append(commandChip);
        }
        return line;
    }

    private static MutableComponent buildCommandTipChip(String labelKey, String detailKey, String suggestion, ChatFormatting color) {
        MutableComponent chip = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY);
        chip.append(Component.translatable(labelKey).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(detailKey)))));
        chip.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        return chip;
    }

    private record ClosestPointResult(Vec3 point, double progress) {
    }

    private record RescueProgress(UUID targetId, InteractionHand hand, int ticks, int durationTicks) {
        private boolean matches(InteractionTarget rescueTarget, int rescueDurationTicks) {
            return targetId.equals(rescueTarget.target().getUUID()) && hand == rescueTarget.hand() && durationTicks == rescueDurationTicks;
        }

        private RescueProgress withTicks(int updatedTicks) {
            return new RescueProgress(targetId, hand, updatedTicks, durationTicks);
        }
    }

    private record ExecutionProgress(UUID targetId, InteractionHand hand, int ticks) {
        private boolean matches(InteractionTarget executionTarget) {
            return targetId.equals(executionTarget.target().getUUID()) && hand == executionTarget.hand();
        }

        private ExecutionProgress withTicks(int updatedTicks) {
            return new ExecutionProgress(targetId, hand, updatedTicks);
        }
    }

    private record InteractionTarget(Player target, InteractionHand hand) {
    }

    private record InteractionSelection(InteractionType type, InteractionHand hand) {
    }

    private enum InteractionType {
        RESCUE,
        EXECUTE
    }
}



