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
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.registries.FirstAidRegistryLookups;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.PlayerSizeHelper;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
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
import net.minecraft.world.food.FoodData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

public final class EventHandler {
    public static final Random RAND = new Random();
    private static final EntityDimensions PLAYER_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(1.4F, 0.4F);
    private static final List<ResourceKey<Recipe<?>>> STARTER_RECIPES = List.of(
            recipeKey("bandage"),
            recipeKey("plaster"),
            recipeKey("morphine"),
            recipeKey("painkillers")
    );
    private static final int RESCUE_DURATION_TICKS = PlayerDamageModel.getRescueDurationTicks();

    public static final Map<Player, ProjectileHitContext> hitList = new WeakHashMap<>();
    private static final Map<UUID, RescueProgress> rescueProgress = new HashMap<>();

    private EventHandler() {
    }

    public static void registerServerEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EventHandler::onAllowDamage);
        ServerTickEvents.END_WORLD_TICK.register(EventHandler::tickPlayers);
        EntitySleepEvents.STOP_SLEEPING.register(EventHandler::onStopSleeping);
        LootTableEvents.MODIFY.register(EventHandler::onLootTableModify);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DebugDamageCommand.register(dispatcher);
            FirstAidCommand.register(dispatcher);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onLogin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLogout(handler.getPlayer()));
        ServerPlayerEvents.COPY_FROM.register(EventHandler::onCopyFrom);
        ServerPlayerEvents.AFTER_RESPAWN.register(EventHandler::onAfterRespawn);
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> onDimensionChange(player));
        ServerWorldEvents.LOAD.register((server, world) -> onWorldLoad(world));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FirstAidConfig.applyCommandSettings();
            FirstAidRegistryLookups.init(server.registryAccess(), false);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                FirstAidRegistryLookups.init(server.registryAccess(), false);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> onServerStop());

        UseEntityCallback.EVENT.register(EventHandler::onEntityInteract);
        UseItemCallback.EVENT.register((player, level, hand) -> {
            InteractionResult result = onItemUse(player, level, hand);
            return result == InteractionResult.PASS
                    ? InteractionResultHolder.pass(player.getItemInHand(hand))
                    : InteractionResultHolder.fail(player.getItemInHand(hand));
        });
        UseBlockCallback.EVENT.register(EventHandler::onBlockInteract);
        AttackEntityCallback.EVENT.register(EventHandler::onAttackEntity);
        AttackBlockCallback.EVENT.register(EventHandler::onBlockAttack);
    }

    public static void recordProjectileHit(Player player, Entity projectile, Vec3 hitPosition) {
        hitList.put(player, new ProjectileHitContext(projectile, hitPosition));
    }

    private static ResourceKey<Recipe<?>> recipeKey(String path) {
        return ResourceKey.create(Registries.RECIPE, ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, path));
    }

    private static void awardStarterRecipes(ServerPlayer player) {
        List<RecipeHolder<?>> recipes = STARTER_RECIPES.stream()
                .map(recipeKey -> Objects.requireNonNull(player.level().getServer()).getRecipeManager().byKey(recipeKey.location()))
                .flatMap(Optional::stream)
                .toList();
        if (!recipes.isEmpty()) {
            player.awardRecipes(recipes);
        }
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide() || !CommonUtils.hasDamageModel(entity)) {
            return true;
        }
        Player player = (Player) entity;
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return true;
        }
        if (isProtectedUnconsciousSuffocation(damageModel, source)) {
            hitList.remove(player);
            return false;
        }

        if (amount == Float.MAX_VALUE || Float.isNaN(amount) || amount == Float.POSITIVE_INFINITY) {
            damageModel.forEach(damageablePart -> damageablePart.currentHealth = 0F);
            if (player instanceof ServerPlayer serverPlayer) {
                FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            }
            CommonUtils.killPlayer(damageModel, player, source);
            return false;
        }

        boolean addStat = amount < 3.4028235E37F;
        IDamageDistributionAlgorithm damageDistribution = FirstAidRegistryLookups.getDamageDistributions(source.type());

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
            damageDistribution = PlayerSizeHelper.getMeleeDistribution(player, source);
            if (damageDistribution == null) {
                damageDistribution = RandomDamageDistributionAlgorithm.getDefault();
            }
        }

        DamageDistribution.handleDamageTaken(damageDistribution, damageModel, amount, player, source, addStat, true);
        if (amount > 0.0F && player.isAlive()) {
            float pitch = 0.9F + (player.level().random.nextFloat() * 0.2F);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, player.getSoundSource(), 1.0F, pitch);
        }

        hitList.remove(player);
        return false;
    }

    private record ProjectileHitContext(Entity projectile, Vec3 hitPosition) {
    }

    private static void tickPlayers(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            if (!player.getAbilities().invulnerable) {
                if (!player.isAlive()) {
                    continue;
                }
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) {
                    continue;
                }
                if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                    float nearMissStrength = getNearbyProjectileStrength(player);
                    if (nearMissStrength > 0.0F) {
                        playerDamageModel.registerAdrenalineNearMiss(nearMissStrength);
                    }
                    if (playerDamageModel.isUnconscious()) {
                        clearAttackTargetsAround(player, 24.0D);
                    }
                }
                damageModel.tick(player.level(), player);
                tickRescueProgress(player);
                hitList.remove(player);
            }
        }
    }

    private static void onStopSleeping(LivingEntity entity, net.minecraft.core.BlockPos sleepingPos) {
        if (entity.level().isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        if (FabricLoader.getInstance().isModLoaded("morpheus")) {
            return;
        }
        if (player.isSleepingLongEnough()) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) {
                return;
            }
            damageModel.sleepHeal(player);
        }
    }

    private static void onLootTableModify(ResourceKey<net.minecraft.world.level.storage.loot.LootTable> key,
                                          net.minecraft.world.level.storage.loot.LootTable.Builder tableBuilder,
                                          net.fabricmc.fabric.api.loot.v3.LootTableSource source,
                                          net.minecraft.core.HolderLookup.Provider registries) {
        int bandage;
        int plaster;
        int morphine;
        NumberProvider bandageMax = UniformGenerator.between(1, 3);
        NumberProvider plasterMax = UniformGenerator.between(1, 5);
        NumberProvider morphineMax = UniformGenerator.between(1, 2);
        NumberProvider poolRolls = ConstantValue.exactly(1.0F);
        if (key.equals(BuiltInLootTables.SPAWN_BONUS_CHEST)) {
            bandage = 8;
            plaster = 16;
            morphine = 4;
            morphineMax = ConstantValue.exactly(1);
        } else if (key.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR) || key.equals(BuiltInLootTables.STRONGHOLD_CROSSING) || key.equals(BuiltInLootTables.ABANDONED_MINESHAFT)) {
            bandage = 20;
            plaster = 24;
            morphine = 8;
            poolRolls = UniformGenerator.between(0, 1);
        } else if (key.equals(BuiltInLootTables.VILLAGE_BUTCHER)) {
            bandage = 4;
            plaster = 20;
            morphine = 2;
            plasterMax = UniformGenerator.between(3, 8);
        } else if (key.equals(BuiltInLootTables.IGLOO_CHEST)) {
            bandage = 4;
            plaster = 8;
            morphine = 2;
            poolRolls = UniformGenerator.between(0, 1);
        } else if (key.equals(BuiltInLootTables.SHIPWRECK_SUPPLY)) {
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
        LootPool.Builder builder = LootPool.lootPool().setRolls(poolRolls);
        builder.add(LootItem.lootTableItem(RegistryObjects.BANDAGE.get())
                .apply(SetItemCountFunction.setCount(bandageMax))
                .setWeight(bandage)
                .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.PLASTER.get())
                .apply(SetItemCountFunction.setCount(plasterMax))
                .setWeight(plaster)
                .setQuality(0));
        builder.add(LootItem.lootTableItem(RegistryObjects.MORPHINE.get())
                .apply(SetItemCountFunction.setCount(morphineMax))
                .setWeight(morphine)
                .setQuality(0));
        tableBuilder.withPool(builder);
    }

    private static InteractionResult onEntityInteract(Player rescuer, net.minecraft.world.level.Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
        if (level.isClientSide()) {
            if (isUnconscious(rescuer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        if (isUnconscious(rescuer)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult onItemUse(Player player, net.minecraft.world.level.Level level, InteractionHand hand) {
        return cancelIfUnconscious(player);
    }

    private static InteractionResult onBlockInteract(Player player, net.minecraft.world.level.Level level, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        return cancelIfUnconscious(player);
    }

    private static InteractionResult onBlockAttack(Player player, net.minecraft.world.level.Level level, InteractionHand hand, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        return cancelIfUnconscious(player);
    }

    private static InteractionResult onAttackEntity(Player player, net.minecraft.world.level.Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
        return cancelIfUnconscious(player);
    }

    private static void onLogin(ServerPlayer player) {
        if (player.level().isClientSide()) {
            return;
        }
        FirstAid.LOGGER.debug("Sending damage model to {}", player.getName());
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return;
        }
        if (damageModel.hasTutorial) {
            CapProvider.tutorialDone.add(player.getName().getString());
        }
        awardStarterRecipes(player);
        FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        FirstAidNetworking.sendServerConfig(player);
        sendOpCommandTip(player);
    }

    private static void onLogout(ServerPlayer player) {
        hitList.remove(player);
        rescueProgress.remove(player.getUUID());
    }

    private static void onWorldLoad(ServerLevel world) {
        world.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(FirstAidConfig.SERVER.allowNaturalRegeneration.get(), world.getServer());
    }

    private static void onDimensionChange(ServerPlayer player) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return;
        }
        FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        FirstAidNetworking.sendServerConfig(player);
    }

    private static void onCopyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        AbstractPlayerDamageModel oldModel = CommonUtils.getExistingDamageModel(oldPlayer);
        if (oldModel instanceof PlayerDamageModel oldDamageModel && newPlayer instanceof FirstAidDamageModelHolder holder) {
            PlayerDamageModel cloned = new PlayerDamageModel();
            cloned.deserializeNBT(oldDamageModel.serializeNBT());
            holder.firstaid$setDamageModel(cloned);
        }
    }

    private static void onAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (alive) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(newPlayer);
        if (damageModel == null) {
            return;
        }
        damageModel.runScaleLogic(newPlayer);
        damageModel.forEach(damageablePart -> damageablePart.heal(damageablePart.getMaxHealth(), newPlayer, false));
        if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            playerDamageModel.clearStatusEffects();
        }
        damageModel.scheduleResync();
    }

    private static void onServerStop() {
        FirstAid.LOGGER.debug("Cleaning up");
        FirstAid.dynamicPainEnabled = true;
        FirstAid.lowSuppressionEnabled = false;
        FirstAid.rescueWakeUpEnabled = false;
        FirstAid.medicineEffectMode = FirstAid.MedicineEffectMode.REALISTIC;
        FirstAid.injuryDebuffMode = FirstAid.InjuryDebuffMode.NORMAL;
        FirstAid.injuryDebuffOverrides.clear();
        CapProvider.tutorialDone.clear();
        hitList.clear();
        rescueProgress.clear();
        FirstAidRegistryLookups.reset();
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

    private static InteractionResult cancelIfUnconscious(Player player) {
        return isUnconscious(player) ? InteractionResult.FAIL : InteractionResult.PASS;
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
        List<Mob> mobs = victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(range));
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

    private static void sendOpCommandTip(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            return;
        }
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.pain").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.suppression").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.medicineeffect").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.injurydebuff").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.revivewakeup").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.translatable("firstaid.tip.commands.damagepart").withStyle(ChatFormatting.YELLOW), false);
    }

    private static void tickRescueProgress(ServerPlayer rescuer) {
        RescueTarget rescueTarget = findRescueTarget(rescuer, true);
        if (rescueTarget == null) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }

        RescueProgress progress = rescueProgress.get(rescuer.getUUID());
        if (progress == null || !progress.matches(rescueTarget)) {
            progress = new RescueProgress(rescueTarget.target().getUUID(), rescueTarget.hand(), 0);
        }

        int nextTicks = Math.min(RESCUE_DURATION_TICKS, progress.ticks() + 1);
        if (nextTicks < RESCUE_DURATION_TICKS) {
            rescueProgress.put(rescuer.getUUID(), progress.withTicks(nextTicks));
            return;
        }

        completeRescue(rescuer, rescueTarget);
    }

    public static void attemptImmediateRescue(ServerPlayer rescuer) {
        RescueTarget rescueTarget = findRescueTarget(rescuer, true);
        if (rescueTarget == null) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }
        completeRescue(rescuer, rescueTarget);
    }

    private static void completeRescue(ServerPlayer rescuer, RescueTarget rescueTarget) {
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

        stack.shrink(1);
        if (playerDamageModel.rescueFromCriticalState(rescueTarget.target(), null, FirstAid.rescueWakeUpEnabled)) {
            rescuer.displayClientMessage(Component.translatable("firstaid.gui.rescue_other", rescueTarget.target().getDisplayName()).withStyle(ChatFormatting.GREEN), true);
            rescueTarget.target().displayClientMessage(Component.translatable("firstaid.gui.rescue_received", rescuer.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        }
        rescueProgress.remove(rescuer.getUUID());
    }

    private static RescueTarget findRescueTarget(Player rescuer, boolean requireSneaking) {
        if (rescuer == null || rescuer.level().isClientSide() || isUnconscious(rescuer)) {
            return null;
        }
        if (requireSneaking && !rescuer.isCrouching()) {
            return null;
        }

        InteractionHand hand = getRescueHand(rescuer);
        if (hand == null) {
            return null;
        }

        double maxDistanceSqr = PlayerDamageModel.getRescueRange() * PlayerDamageModel.getRescueRange();
        Player closestTarget = null;
        double closestDistanceSqr = maxDistanceSqr;
        for (Player candidate : rescuer.level().players()) {
            if (candidate == rescuer || !candidate.isAlive()) {
                continue;
            }
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(candidate);
            if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
                continue;
            }
            double distanceSqr = rescuer.distanceToSqr(candidate);
            if (distanceSqr > closestDistanceSqr) {
                continue;
            }
            closestDistanceSqr = distanceSqr;
            closestTarget = candidate;
        }
        return closestTarget == null ? null : new RescueTarget(closestTarget, hand);
    }

    private static InteractionHand getRescueHand(Player player) {
        if (isRescueItem(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (isRescueItem(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static boolean isRescueItem(ItemStack stack) {
        return stack.is(RegistryObjects.BANDAGE.get()) || stack.is(RegistryObjects.PLASTER.get());
    }

    private record ClosestPointResult(Vec3 point, double progress) {
    }

    private record RescueTarget(Player target, InteractionHand hand) {
    }

    private record RescueProgress(UUID targetId, InteractionHand hand, int ticks) {
        private boolean matches(RescueTarget rescueTarget) {
            return targetId.equals(rescueTarget.target().getUUID()) && hand == rescueTarget.hand();
        }

        private RescueProgress withTicks(int updatedTicks) {
            return new RescueProgress(targetId, hand, updatedTicks);
        }
    }
}
