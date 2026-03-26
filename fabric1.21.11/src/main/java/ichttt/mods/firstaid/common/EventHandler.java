package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.registries.FirstAidRegistryLookups;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.PlayerSizeHelper;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AfterPlayerChange;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.EndDataPackReload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.Load;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTable.Builder;
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
import net.minecraft.world.phys.HitResult.Type;

public final class EventHandler {
   public static final Random RAND = new Random();
   private static final EntityDimensions PLAYER_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(1.4F, 0.4F);
   private static final List<ResourceKey<Recipe<?>>> STARTER_RECIPES = List.of(
      recipeKey("bandage"), recipeKey("plaster"), recipeKey("morphine"), recipeKey("painkillers")
   );
   private static final int RESCUE_DURATION_TICKS = PlayerDamageModel.getRescueDurationTicks();
   private static final int EXECUTION_DURATION_TICKS = PlayerDamageModel.getExecutionDurationTicks();
   public static final Map<Player, EventHandler.ProjectileHitContext> hitList = new WeakHashMap<>();
   private static final Map<UUID, EventHandler.RescueProgress> rescueProgress = new HashMap<>();
   private static final Map<UUID, EventHandler.ExecutionProgress> executionProgress = new HashMap<>();
   private static final IDamageDistributionAlgorithm FOOT_ONLY_DAMAGE_DISTRIBUTION = new StandardDamageDistributionAlgorithm(
      Collections.singletonMap(EquipmentSlot.FEET, CommonUtils.getPartListForSlot(EquipmentSlot.FEET)),
      false,
      true
   );

   private EventHandler() {
   }

   public static void registerServerEvents() {
      ServerTickEvents.END_WORLD_TICK.register(EventHandler::tickPlayers);
      EntitySleepEvents.STOP_SLEEPING.register(EventHandler::onStopSleeping);
      LootTableEvents.MODIFY.register(EventHandler::onLootTableModify);
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
         DebugDamageCommand.register(dispatcher);
         FirstAidCommand.register(dispatcher);
      });
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> onLogin(handler.getPlayer()));
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> onLogout(handler.getPlayer()));
      ServerPlayerEvents.COPY_FROM.register(EventHandler::onCopyFrom);
      ServerPlayerEvents.AFTER_RESPAWN.register(EventHandler::onAfterRespawn);
      ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((AfterPlayerChange)(player, origin, destination) -> onDimensionChange(player));
      ServerWorldEvents.LOAD.register((Load)(server, world) -> onWorldLoad(world));
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
         FirstAidConfig.applyCommandSettings();
         FirstAidRegistryLookups.init(server.registryAccess(), false);
      });
      ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((EndDataPackReload)(server, resourceManager, success) -> {
         if (success) {
            FirstAidRegistryLookups.init(server.registryAccess(), false);
         }
      });
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> onServerStop());
      UseEntityCallback.EVENT.register(EventHandler::onEntityInteract);
      UseItemCallback.EVENT.register(EventHandler::onItemUse);
      UseBlockCallback.EVENT.register(EventHandler::onBlockInteract);
      AttackEntityCallback.EVENT.register(EventHandler::onAttackEntity);
      AttackBlockCallback.EVENT.register(EventHandler::onBlockAttack);
   }

   public static void recordProjectileHit(Player player, Entity projectile, Vec3 hitPosition) {
      hitList.put(player, new EventHandler.ProjectileHitContext(projectile, hitPosition));
   }

   private static ResourceKey<Recipe<?>> recipeKey(String path) {
      return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("firstaid", path));
   }

   private static void awardStarterRecipes(ServerPlayer player) {
      List<RecipeHolder<?>> recipes = STARTER_RECIPES.stream()
         .map(Objects.requireNonNull(player.level().getServer()).getRecipeManager()::byKey)
         .flatMap(Optional::stream)
         .toList();
      if (!recipes.isEmpty()) {
         player.awardRecipes(recipes);
      }
   }

   public static Boolean preHandleCustomPlayerDamage(Player player, DamageSource source, float amount) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      if (damageModel == null) {
         return null;
      } else if (isProtectedUnconsciousSuffocation(damageModel, source)) {
         hitList.remove(player);
         return false;
      } else if (amount != Float.MAX_VALUE && !Float.isNaN(amount) && amount != Float.POSITIVE_INFINITY) {
         return null;
      } else {
         damageModel.forEach(damageablePart -> damageablePart.currentHealth = 0.0F);
         if (player instanceof ServerPlayer serverPlayer) {
            FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
         }

         CommonUtils.killPlayer(damageModel, player, source);
         hitList.remove(player);
         return true;
      }
   }

   public static boolean handleCustomPlayerDamage(Player player, DamageSource source, float amount) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      if (damageModel == null) {
         return false;
      } else {
         boolean addStat = amount < 3.4028235E37F;
         IDamageDistributionAlgorithm damageDistribution = getForcedDamageDistribution(source);
         if (damageDistribution == null) {
            damageDistribution = FirstAidRegistryLookups.getDamageDistributions(source.type());
         }
         if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            Entity directEntity = source.getDirectEntity();
            EventHandler.ProjectileHitContext projectileHitContext = hitList.remove(player);
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

         IDamageDistributionAlgorithm finalDamageDistribution = damageDistribution;
         float finalAmount = amount;
         boolean redistributeLeftoverDamage = shouldRedistributeLeftoverDamage(source);
         CommonUtils.runWithoutSetHealthInterception(
            () -> DamageDistribution.handleDamageTaken(finalDamageDistribution, damageModel, finalAmount, player, source, addStat, redistributeLeftoverDamage)
         );
         hitList.remove(player);
         return true;
      }
   }

   public static IDamageDistributionAlgorithm getForcedDamageDistribution(DamageSource source) {
      return CommonUtils.isFootOnlyDamageSource(source) ? FOOT_ONLY_DAMAGE_DISTRIBUTION : null;
   }

   private static boolean shouldRedistributeLeftoverDamage(DamageSource source) {
      return !CommonUtils.isFootOnlyDamageSource(source);
   }

   private static void tickPlayers(ServerLevel world) {
      for (ServerPlayer player : world.players()) {
         if (!player.getAbilities().invulnerable && player.isAlive()) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel != null) {
               if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                  float nearMissStrength = getNearbyProjectileStrength(player);
                  if (nearMissStrength > 0.0F) {
                     playerDamageModel.registerAdrenalineNearMiss(nearMissStrength);
                  }

                  if (playerDamageModel.isUnconscious()) {
                     clearAttackTargetsAround(player, 24.0);
                  }
               }

               damageModel.tick(player.level(), player);
               tickRescueProgress(player);
               tickExecutionProgress(player);
               hitList.remove(player);
            }
         }
      }
   }

   private static void onStopSleeping(LivingEntity entity, BlockPos sleepingPos) {
      if (!entity.level().isClientSide() && entity instanceof Player player) {
         if (!FabricLoader.getInstance().isModLoaded("morpheus")) {
            if (player.isSleepingLongEnough()) {
               AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
               if (damageModel == null) {
                  return;
               }

               damageModel.sleepHeal(player);
            }
         }
      }
   }

   private static void onLootTableModify(ResourceKey<LootTable> key, Builder tableBuilder, LootTableSource source, Provider registries) {
      NumberProvider bandageMax = UniformGenerator.between(1.0F, 3.0F);
      NumberProvider plasterMax = UniformGenerator.between(1.0F, 5.0F);
      NumberProvider morphineMax = UniformGenerator.between(1.0F, 2.0F);
      NumberProvider poolRolls = ConstantValue.exactly(1.0F);
      int bandage;
      int plaster;
      int morphine;
      if (key.equals(BuiltInLootTables.SPAWN_BONUS_CHEST)) {
         bandage = 8;
         plaster = 16;
         morphine = 4;
         morphineMax = ConstantValue.exactly(1.0F);
      } else if (key.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR)
         || key.equals(BuiltInLootTables.STRONGHOLD_CROSSING)
         || key.equals(BuiltInLootTables.ABANDONED_MINESHAFT)) {
         bandage = 20;
         plaster = 24;
         morphine = 8;
         poolRolls = UniformGenerator.between(0.0F, 1.0F);
      } else if (key.equals(BuiltInLootTables.VILLAGE_BUTCHER)) {
         bandage = 4;
         plaster = 20;
         morphine = 2;
         plasterMax = UniformGenerator.between(3.0F, 8.0F);
      } else if (key.equals(BuiltInLootTables.IGLOO_CHEST)) {
         bandage = 4;
         plaster = 8;
         morphine = 2;
         poolRolls = UniformGenerator.between(0.0F, 1.0F);
      } else {
         if (!key.equals(BuiltInLootTables.SHIPWRECK_SUPPLY)) {
            return;
         }

         bandage = 4;
         plaster = 8;
         morphine = 2;
         bandageMax = UniformGenerator.between(1.0F, 2.0F);
         plasterMax = UniformGenerator.between(1.0F, 3.0F);
         morphineMax = ConstantValue.exactly(1.0F);
         poolRolls = UniformGenerator.between(0.0F, 1.0F);
      }

      net.minecraft.world.level.storage.loot.LootPool.Builder builder = LootPool.lootPool().setRolls(poolRolls);
      builder.add(
         LootItem.lootTableItem((ItemLike)RegistryObjects.BANDAGE.get()).apply(SetItemCountFunction.setCount(bandageMax)).setWeight(bandage).setQuality(0)
      );
      builder.add(
         LootItem.lootTableItem((ItemLike)RegistryObjects.PLASTER.get()).apply(SetItemCountFunction.setCount(plasterMax)).setWeight(plaster).setQuality(0)
      );
      builder.add(
         LootItem.lootTableItem((ItemLike)RegistryObjects.MORPHINE.get()).apply(SetItemCountFunction.setCount(morphineMax)).setWeight(morphine).setQuality(0)
      );
      tableBuilder.withPool(builder);
   }

   private static InteractionResult onEntityInteract(Player rescuer, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
      if (level.isClientSide()) {
         return (InteractionResult)(isUnconscious(rescuer) ? InteractionResult.FAIL : InteractionResult.PASS);
      } else {
         return (InteractionResult)(isUnconscious(rescuer) ? InteractionResult.FAIL : InteractionResult.PASS);
      }
   }

   private static InteractionResult onItemUse(Player player, Level level, InteractionHand hand) {
      return cancelIfUnconscious(player);
   }

   private static InteractionResult onBlockInteract(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
      return cancelIfUnconscious(player);
   }

   private static InteractionResult onBlockAttack(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
      return cancelIfUnconscious(player);
   }

   private static InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
      return cancelIfUnconscious(player);
   }

   private static void onLogin(ServerPlayer player) {
      if (!player.level().isClientSide()) {
         FirstAid.LOGGER.debug("Sending damage model to {}", player.getName());
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
         if (damageModel != null) {
            if (damageModel.hasTutorial) {
               CapProvider.tutorialDone.add(player.getName().getString());
            }

            awardStarterRecipes(player);
            FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            FirstAidNetworking.sendServerConfig(player);
            sendOpCommandTip(player);
         }
      }
   }

   private static void onLogout(ServerPlayer player) {
      hitList.remove(player);
      rescueProgress.remove(player.getUUID());
      executionProgress.remove(player.getUUID());
   }

   private static void onWorldLoad(ServerLevel world) {
      world.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, FirstAidConfig.SERVER.allowNaturalRegeneration.get(), world.getServer());
   }

   private static void onDimensionChange(ServerPlayer player) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      if (damageModel != null) {
         FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
         FirstAidNetworking.sendServerConfig(player);
      }
   }

   private static void onCopyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
      if (CommonUtils.getExistingDamageModel(oldPlayer) instanceof PlayerDamageModel oldDamageModel && newPlayer instanceof FirstAidDamageModelHolder holder) {
         PlayerDamageModel cloned = new PlayerDamageModel();
         cloned.deserializeNBT(oldDamageModel.serializeNBT());
         holder.firstaid$setDamageModel(cloned);
      }
   }

   private static void onAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
      if (!alive) {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(newPlayer);
         if (damageModel != null) {
            damageModel.runScaleLogic(newPlayer);
            damageModel.forEach(damageablePart -> damageablePart.heal(damageablePart.getMaxHealth(), newPlayer, false));
            if (damageModel instanceof PlayerDamageModel playerDamageModel) {
               playerDamageModel.clearStatusEffects();
            }

            damageModel.scheduleResync();
         }
      }
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
      executionProgress.clear();
      FirstAidRegistryLookups.reset();
   }

   private static float getNearbyProjectileStrength(Player player) {
      AABB scanBox = player.getBoundingBox().inflate(3.25);
      AABB playerBox = player.getBoundingBox().inflate(0.12);
      Vec3 eyePosition = player.getEyePosition();
      Vec3 torsoPosition = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
      float strongest = 0.0F;

      for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, scanBox, projectilex -> {
         if (!projectilex.isAlive() || projectilex.getOwner() == player) {
            return false;
         } else {
            return projectilex instanceof AbstractThrownPotion ? false : projectilex.getDeltaMovement().lengthSqr() >= 0.02;
         }
      })) {
         Vec3 currentPosition = projectile.position();
         Vec3 previousPosition = currentPosition.subtract(projectile.getDeltaMovement());
         Vec3 endPosition = currentPosition.add(projectile.getDeltaMovement());
         if (!playerBox.intersects(projectile.getBoundingBox()) && !playerBox.clip(previousPosition, endPosition).isPresent()) {
            strongest = Math.max(strongest, getNearMissStrength(player, projectile, previousPosition, endPosition, eyePosition));
            strongest = Math.max(strongest, getNearMissStrength(player, projectile, previousPosition, endPosition, torsoPosition));
         }
      }

      return strongest;
   }

   private static float getNearMissStrength(Player player, Projectile projectile, Vec3 start, Vec3 end, Vec3 target) {
      EventHandler.ClosestPointResult closestPointResult = closestPointOnSegment(start, end, target);
      if (!(closestPointResult.progress <= 0.0) && !(closestPointResult.progress >= 1.0)) {
         double distance = closestPointResult.point.distanceTo(target);
         if (distance > 1.85) {
            return 0.0F;
         } else {
            BlockHitResult hitResult = player.level().clip(new ClipContext(closestPointResult.point, target, Block.COLLIDER, Fluid.NONE, player));
            if (hitResult.getType() != Type.MISS) {
               return 0.0F;
            } else {
               double speed = projectile.getDeltaMovement().length();
               float speedFactor = Mth.clamp((float)((speed - 0.18) / 0.65), 0.0F, 1.0F);
               float distanceFactor = Mth.clamp(1.32F - (float)(distance / 1.85), 0.0F, 1.0F);
               return Mth.clamp(distanceFactor * (0.82F + 0.58F * speedFactor), 0.0F, 1.45F);
            }
         }
      } else {
         return 0.0F;
      }
   }

   private static EventHandler.ClosestPointResult closestPointOnSegment(Vec3 start, Vec3 end, Vec3 target) {
      Vec3 segment = end.subtract(start);
      double lengthSqr = segment.lengthSqr();
      if (lengthSqr < 1.0E-7) {
         return new EventHandler.ClosestPointResult(start, 0.0);
      } else {
         double progress = Mth.clamp(target.subtract(start).dot(segment) / lengthSqr, 0.0, 1.0);
         return new EventHandler.ClosestPointResult(start.add(segment.scale(progress)), progress);
      }
   }

   private static InteractionResult cancelIfUnconscious(Player player) {
      return (InteractionResult)(isUnconscious(player) ? InteractionResult.FAIL : InteractionResult.PASS);
   }

   private static boolean isUnconscious(Player player) {
      return isUnconscious(player, true);
   }

   private static boolean isUnconscious(Player player, boolean allowCreate) {
      return (allowCreate ? CommonUtils.getDamageModel(player) : CommonUtils.getExistingDamageModel(player)) instanceof PlayerDamageModel playerDamageModel
         && playerDamageModel.isUnconscious();
   }

   private static boolean isProtectedUnconsciousSuffocation(AbstractPlayerDamageModel damageModel, DamageSource source) {
      return damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious() && source.is(DamageTypes.IN_WALL);
   }

   private static void clearAttackTargetsAround(LivingEntity victim, double range) {
      for (Mob mob : victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(range))) {
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
      if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
         player.displayClientMessage(
            Component.translatable("firstaid.tip.commands.header").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), false
         );
         player.displayClientMessage(Component.translatable("firstaid.tip.commands.pain").withStyle(ChatFormatting.YELLOW), false);
         player.displayClientMessage(Component.translatable("firstaid.tip.commands.suppression").withStyle(ChatFormatting.YELLOW), false);
         player.displayClientMessage(Component.translatable("firstaid.tip.commands.medicineeffect").withStyle(ChatFormatting.YELLOW), false);
         player.displayClientMessage(Component.translatable("firstaid.tip.commands.injurydebuff").withStyle(ChatFormatting.YELLOW), false);
         player.displayClientMessage(Component.translatable("firstaid.tip.commands.revivewakeup").withStyle(ChatFormatting.YELLOW), false);
         player.displayClientMessage(Component.translatable("firstaid.tip.commands.damagepart").withStyle(ChatFormatting.YELLOW), false);
      }
   }

   private static void tickRescueProgress(ServerPlayer rescuer) {
      EventHandler.InteractionTarget rescueTarget = findRescueTarget(rescuer, true);
      if (rescueTarget == null) {
         rescueProgress.remove(rescuer.getUUID());
      } else {
         EventHandler.RescueProgress progress = rescueProgress.get(rescuer.getUUID());
         if (progress == null || !progress.matches(rescueTarget)) {
            progress = new EventHandler.RescueProgress(rescueTarget.target().getUUID(), rescueTarget.hand(), 0);
         }

         int nextTicks = Math.min(RESCUE_DURATION_TICKS, progress.ticks() + 1);
         if (nextTicks < RESCUE_DURATION_TICKS) {
            rescueProgress.put(rescuer.getUUID(), progress.withTicks(nextTicks));
         } else {
            completeRescue(rescuer, rescueTarget);
         }
      }
   }

   public static void attemptImmediateRescue(ServerPlayer rescuer) {
      EventHandler.InteractionTarget rescueTarget = findRescueTarget(rescuer, true);
      if (rescueTarget == null) {
         rescueProgress.remove(rescuer.getUUID());
      } else {
         completeRescue(rescuer, rescueTarget);
      }
   }

   private static void tickExecutionProgress(ServerPlayer executor) {
      EventHandler.InteractionTarget executionTarget = findExecutionTarget(executor, true);
      if (executionTarget == null) {
         executionProgress.remove(executor.getUUID());
      } else {
         EventHandler.ExecutionProgress progress = executionProgress.get(executor.getUUID());
         if (progress == null || !progress.matches(executionTarget)) {
            progress = new EventHandler.ExecutionProgress(executionTarget.target().getUUID(), executionTarget.hand(), 0);
         }

         int nextTicks = Math.min(EXECUTION_DURATION_TICKS, progress.ticks() + 1);
         if (nextTicks < EXECUTION_DURATION_TICKS) {
            executionProgress.put(executor.getUUID(), progress.withTicks(nextTicks));
         } else {
            completeExecution(executor, executionTarget);
         }
      }
   }

   public static void attemptImmediateExecution(ServerPlayer executor) {
      EventHandler.InteractionTarget executionTarget = findExecutionTarget(executor, true);
      if (executionTarget == null) {
         executionProgress.remove(executor.getUUID());
      } else {
         completeExecution(executor, executionTarget);
      }
   }

   private static void completeRescue(ServerPlayer rescuer, EventHandler.InteractionTarget rescueTarget) {
      ItemStack stack = rescuer.getItemInHand(rescueTarget.hand());
      if (!isRescueItem(stack)) {
         rescueProgress.remove(rescuer.getUUID());
      } else if (CommonUtils.getDamageModel(rescueTarget.target()) instanceof PlayerDamageModel playerDamageModel && playerDamageModel.canBeRescued()) {
         stack.shrink(1);
         if (playerDamageModel.rescueFromCriticalState(rescueTarget.target(), null, FirstAid.rescueWakeUpEnabled)) {
            rescuer.displayClientMessage(
               Component.translatable("firstaid.gui.rescue_other", new Object[]{rescueTarget.target().getDisplayName()}).withStyle(ChatFormatting.GREEN), true
            );
            rescueTarget.target()
               .displayClientMessage(
                  Component.translatable("firstaid.gui.rescue_received", new Object[]{rescuer.getDisplayName()}).withStyle(ChatFormatting.GREEN), true
               );
         }

         rescueProgress.remove(rescuer.getUUID());
      } else {
         rescueProgress.remove(rescuer.getUUID());
      }
   }

   private static void completeExecution(ServerPlayer executor, EventHandler.InteractionTarget executionTarget) {
      ItemStack stack = executor.getItemInHand(executionTarget.hand());
      if (!CommonUtils.isExecutionItem(stack)) {
         executionProgress.remove(executor.getUUID());
      } else if (CommonUtils.getDamageModel(executionTarget.target()) instanceof PlayerDamageModel playerDamageModel && playerDamageModel.canBeRescued()) {
         if (stack.isDamageableItem()) {
            stack.hurtAndBreak(1, executor, getEquipmentSlot(executionTarget.hand()));
         }

         executor.displayClientMessage(
            Component.translatable("firstaid.gui.execute_other", new Object[]{executionTarget.target().getDisplayName()}).withStyle(ChatFormatting.RED), true
         );
         executionTarget.target()
            .displayClientMessage(
               Component.translatable("firstaid.gui.execute_received", new Object[]{executor.getDisplayName()}).withStyle(ChatFormatting.RED), true
            );
         playerDamageModel.giveUp(executionTarget.target());
         executionProgress.remove(executor.getUUID());
      } else {
         executionProgress.remove(executor.getUUID());
      }
   }

   private static EventHandler.InteractionTarget findRescueTarget(Player rescuer, boolean requireSneaking) {
      if (rescuer != null && !rescuer.level().isClientSide() && !isUnconscious(rescuer)) {
         if (requireSneaking && !rescuer.isCrouching()) {
            return null;
         } else {
            EventHandler.InteractionSelection selection = getInteractionSelection(rescuer);
            if (selection == null || selection.type() != EventHandler.InteractionType.RESCUE) {
               return null;
            } else {
               Player closestTarget = findClosestRescueTarget(rescuer);
               return closestTarget == null ? null : new EventHandler.InteractionTarget(closestTarget, selection.hand());
            }
         }
      } else {
         return null;
      }
   }

   private static EventHandler.InteractionTarget findExecutionTarget(Player executor, boolean requireSneaking) {
      if (executor != null && !executor.level().isClientSide() && !isUnconscious(executor)) {
         if (requireSneaking && !executor.isCrouching()) {
            return null;
         } else {
            EventHandler.InteractionSelection selection = getInteractionSelection(executor);
            if (selection == null || selection.type() != EventHandler.InteractionType.EXECUTE) {
               return null;
            } else {
               Player closestTarget = findClosestRescueTarget(executor);
               return closestTarget == null ? null : new EventHandler.InteractionTarget(closestTarget, selection.hand());
            }
         }
      } else {
         return null;
      }
   }

   private static Player findClosestRescueTarget(Player actor) {
      double maxDistanceSqr = PlayerDamageModel.getRescueRange() * PlayerDamageModel.getRescueRange();
      Player closestTarget = null;
      double closestDistanceSqr = maxDistanceSqr;

      for (Player candidate : actor.level().players()) {
         if (candidate != actor
            && candidate.isAlive()
            && CommonUtils.getDamageModel(candidate) instanceof PlayerDamageModel playerDamageModel
            && playerDamageModel.canBeRescued()) {
            double distanceSqr = actor.distanceToSqr(candidate);
            if (!(distanceSqr > closestDistanceSqr)) {
               closestDistanceSqr = distanceSqr;
               closestTarget = candidate;
            }
         }
      }

      return closestTarget;
   }

   private static EventHandler.InteractionSelection getInteractionSelection(Player player) {
      if (isRescueItem(player.getMainHandItem())) {
         return new EventHandler.InteractionSelection(EventHandler.InteractionType.RESCUE, InteractionHand.MAIN_HAND);
      } else if (CommonUtils.isExecutionItem(player.getMainHandItem())) {
         return new EventHandler.InteractionSelection(EventHandler.InteractionType.EXECUTE, InteractionHand.MAIN_HAND);
      } else if (isRescueItem(player.getOffhandItem())) {
         return new EventHandler.InteractionSelection(EventHandler.InteractionType.RESCUE, InteractionHand.OFF_HAND);
      } else {
         return CommonUtils.isExecutionItem(player.getOffhandItem())
            ? new EventHandler.InteractionSelection(EventHandler.InteractionType.EXECUTE, InteractionHand.OFF_HAND)
            : null;
      }
   }

   private static boolean isRescueItem(ItemStack stack) {
      return stack.is(RegistryObjects.BANDAGE.get()) || stack.is(RegistryObjects.PLASTER.get());
   }

   private static EquipmentSlot getEquipmentSlot(InteractionHand hand) {
      return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
   }

   private record ProjectileHitContext(Entity projectile, Vec3 hitPosition) {
   }

   private record ClosestPointResult(Vec3 point, double progress) {
   }

   private record RescueProgress(UUID targetId, InteractionHand hand, int ticks) {
      private boolean matches(EventHandler.InteractionTarget rescueTarget) {
         return this.targetId.equals(rescueTarget.target().getUUID()) && this.hand == rescueTarget.hand();
      }

      private EventHandler.RescueProgress withTicks(int updatedTicks) {
         return new EventHandler.RescueProgress(this.targetId, this.hand, updatedTicks);
      }
   }

   private record ExecutionProgress(UUID targetId, InteractionHand hand, int ticks) {
      private boolean matches(EventHandler.InteractionTarget executionTarget) {
         return this.targetId.equals(executionTarget.target().getUUID()) && this.hand == executionTarget.hand();
      }

      private EventHandler.ExecutionProgress withTicks(int updatedTicks) {
         return new EventHandler.ExecutionProgress(this.targetId, this.hand, updatedTicks);
      }
   }

   private record InteractionTarget(Player target, InteractionHand hand) {
   }

   private record InteractionSelection(EventHandler.InteractionType type, InteractionHand hand) {
   }

   private static enum InteractionType {
      RESCUE,
      EXECUTE;
   }
}
