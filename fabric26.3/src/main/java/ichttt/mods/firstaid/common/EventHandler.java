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
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.EndDataPackReload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
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
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class EventHandler {
   public static final Random RAND = new Random();
   private static final EntityDimensions PLAYER_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(1.4F, 0.4F);
   private static final int RESCUE_DURATION_TICKS = PlayerDamageModel.getRescueDurationTicks();
   private static final int DEFIBRILLATOR_RESCUE_DURATION_TICKS = PlayerDamageModel.getDefibrillatorRescueDurationTicks();
   private static final int EXECUTION_DURATION_TICKS = PlayerDamageModel.getExecutionDurationTicks();
   public static final Map<Player, EventHandler.ProjectileHitContext> hitList = new WeakHashMap<>();
   private static final Map<UUID, EventHandler.RescueProgress> rescueProgress = new HashMap<>();
    private static final Map<Player, Map<UUID, Long>> recentPlayerAttackers = new WeakHashMap<>();
   private static final Map<UUID, EventHandler.ExecutionProgress> executionProgress = new HashMap<>();
   private static final IDamageDistributionAlgorithm FOOT_ONLY_DAMAGE_DISTRIBUTION = new StandardDamageDistributionAlgorithm(
      Collections.singletonMap(EquipmentSlot.FEET, CommonUtils.getPartListForSlot(EquipmentSlot.FEET)),
      false,
      true
   );

   private EventHandler() {
   }

   public static void registerServerEvents() {
      ServerTickEvents.END_LEVEL_TICK.register(EventHandler::tickPlayers);
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
      ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> onDimensionChange(player));
      ServerLevelEvents.LOAD.register((server, world) -> onWorldLoad(world));
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
      ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damage, blocked) -> {
         if (!(victim instanceof Player) && !blocked && damage > 0.0F) onOffensiveDamage(victim, source);
      });
      ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (!(victim instanceof Player)) onOffensiveDamage(victim, source);
        });
      AttackBlockCallback.EVENT.register(EventHandler::onBlockAttack);
   }

   public static void recordProjectileHit(Player player, Entity projectile, Vec3 hitPosition) {
      hitList.put(player, new EventHandler.ProjectileHitContext(projectile, hitPosition));
   }

   private static void awardStarterRecipes(ServerPlayer player) {
      List<RecipeHolder<?>> recipes = Objects.requireNonNull(player.level().getServer())
         .getRecipeManager()
         .getRecipes()
         .stream()
         .filter(recipe -> "firstaid".equals(recipe.id().identifier().getNamespace()))
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
         boolean hasForcedDamageDistribution = damageDistribution != null;
         if (damageDistribution == null) {
            damageDistribution = FirstAidRegistryLookups.getDamageDistributions(source.type());
         }
         if (source.is(DamageTypeTags.IS_PROJECTILE) && !hasForcedDamageDistribution) {
            Entity directEntity = source.getDirectEntity();
            if (FirstAid.shouldUseFriendlyRandomDistribution()) {
               hitList.remove(player);
               damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_NOKILL;
            } else {
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
                     damageDistribution = new StandardDamageDistributionAlgorithm(
                        Collections.singletonMap(slot, CommonUtils.getPartListForSlot(slot)), false, true
                     );
                  }
               }
               if (damageDistribution == null) {
                  damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_KILL;
               }
            }
         }

         if (damageDistribution == null) {
            if (isMeleeDamageSource(source)) {
               if (FirstAid.shouldUseFriendlyRandomDistribution()) {
                  damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_NOKILL;
               } else {
                  damageDistribution = PlayerSizeHelper.getMeleeDistribution(player, source);
                  if (damageDistribution == null) {
                     damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_KILL;
                  }
               }
            } else {
               damageDistribution = RandomDamageDistributionAlgorithm.getDefault();
            }
         }

         IDamageDistributionAlgorithm finalDamageDistribution = damageDistribution;
         float finalAmount = amount;
         boolean redistributeLeftoverDamage = shouldRedistributeLeftoverDamage(source);
         boolean playerAttack = source.getEntity() instanceof ServerPlayer attacker && attacker != player;
        float previousPartHealth = playerAttack ? totalPartHealth(damageModel) : 0.0f;
        CommonUtils.runWithoutSetHealthInterception(
            () -> DamageDistribution.handleDamageTaken(finalDamageDistribution, damageModel, finalAmount, player, source, addStat, redistributeLeftoverDamage)
         );
         if (playerAttack) {
            float appliedPartDamage = previousPartHealth - totalPartHealth(damageModel);
            if (appliedPartDamage > 0.0f && onOffensiveDamage(player, source)) {
                if (damageModel instanceof PlayerDamageModel victimModel && !victimModel.isUnconscious()) {
                    victimModel.registerAdrenalineNearMiss(player, 0.35f, 40);
                }
            }
        }
        hitList.remove(player);
         return true;
      }
   }

   public static IDamageDistributionAlgorithm getForcedDamageDistribution(DamageSource source) {
      return CommonUtils.isFootOnlyDamageSource(source) ? FOOT_ONLY_DAMAGE_DISTRIBUTION : null;
   }

   private static boolean isMeleeDamageSource(DamageSource source) {
      Entity causingEntity = source.getEntity();
      return causingEntity != null && causingEntity == source.getDirectEntity() && causingEntity instanceof LivingEntity;
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
                     playerDamageModel.registerAdrenalineNearMiss(player, nearMissStrength);
                  }
                  tickEncounterAdrenaline(player, playerDamageModel);

                  if (playerDamageModel.isUnconscious()) {
                     clearAttackTargetsAround(player, 24.0);
                     restrictUnconsciousMovement(player, playerDamageModel);
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
      Holder<ContextIntProvider> bandageMax = ContextIntProviders.between(1, 3);
      Holder<ContextIntProvider> plasterMax = ContextIntProviders.between(1, 5);
      Holder<ContextIntProvider> morphineMax = ContextIntProviders.between(1, 2);
      Holder<ContextIntProvider> poolRolls = ContextIntProviders.exactly(1);
      int bandage;
      int plaster;
      int morphine;
      if (key.equals(BuiltInLootTables.SPAWN_BONUS_CHEST)) {
         bandage = 8;
         plaster = 16;
         morphine = 4;
         morphineMax = ContextIntProviders.exactly(1);
      } else if (key.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR)
         || key.equals(BuiltInLootTables.STRONGHOLD_CROSSING)
         || key.equals(BuiltInLootTables.ABANDONED_MINESHAFT)) {
         bandage = 20;
         plaster = 24;
         morphine = 8;
         poolRolls = ContextIntProviders.between(0, 1);
      } else if (key.equals(BuiltInLootTables.VILLAGE_BUTCHER)) {
         bandage = 4;
         plaster = 20;
         morphine = 2;
         plasterMax = ContextIntProviders.between(3, 8);
      } else if (key.equals(BuiltInLootTables.IGLOO_CHEST)) {
         bandage = 4;
         plaster = 8;
         morphine = 2;
         poolRolls = ContextIntProviders.between(0, 1);
      } else {
         if (!key.equals(BuiltInLootTables.SHIPWRECK_SUPPLY)) {
            return;
         }

         bandage = 4;
         plaster = 8;
         morphine = 2;
         bandageMax = ContextIntProviders.between(1, 2);
         plasterMax = ContextIntProviders.between(1, 3);
         morphineMax = ContextIntProviders.exactly(1);
         poolRolls = ContextIntProviders.between(0, 1);
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

   private static boolean onOffensiveDamage(LivingEntity victim, DamageSource source) {
      if (!FirstAid.projectileSuppressionEnabled || !(victim instanceof Enemy || victim instanceof NeutralMob || victim instanceof Player && victim != source.getEntity())
         || !(source.getEntity() instanceof ServerPlayer attacker) || !attacker.isAlive() || attacker.isSpectator()) return false;
      Entity direct = source.getDirectEntity();
      if (direct != attacker && !(direct instanceof Projectile projectile && projectile.getOwner() == attacker)) return false;
        recordRecentPlayerAttack(victim, attacker);
      if (CommonUtils.getDamageModel(attacker) instanceof PlayerDamageModel model && !model.isUnconscious()) {
         model.registerAdrenalineNearMiss(attacker, 0.35F, 40);
      }
      return true;
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
      world.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, FirstAid.naturalRegenMode != FirstAid.NaturalRegenMode.OFF, world.getServer());
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
               playerDamageModel.beginAudioMute(80);
            }

            damageModel.scheduleResync();
         }
      }
   }

   private static void onServerStop() {
      FirstAid.LOGGER.debug("Cleaning up");
      FirstAid.dynamicPainEnabled = false;
      FirstAid.mildPainLevel = 1;
      FirstAid.enablePainVignette = true;
        FirstAid.enablePainBlur = true;
      FirstAid.enablePainFovCompression = true;
      FirstAid.enablePainAudioEffects = true;
      FirstAid.lowSuppressionEnabled = false;
      FirstAid.projectileSuppressionEnabled = true;
      FirstAid.lowSuppressionMultiplier = 0.4F;
        FirstAid.suppressionGainMultiplier = 0.15F;
      FirstAid.rescueWakeUpEnabled = true;
      FirstAid.rescueWakeUpDelaySeconds = FirstAid.DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS;
      FirstAid.naturalRegenMode = FirstAid.NaturalRegenMode.LIMITED;
      FirstAid.naturalRegenStrategy = FirstAid.NaturalRegenStrategy.CRITICAL;
      FirstAid.naturalRegenLimitRatio = 0.85F;
      FirstAid.naturalRegenCriticalPriorityRatio = 0.85F;
      FirstAid.medicineEffectMode = FirstAid.MedicineEffectMode.REALISTIC;
      FirstAid.medicineTimingMultiplier = 1.0F;
      FirstAid.morphineActivationDelaySeconds = FirstAid.DEFAULT_MORPHINE_ACTIVATION_DELAY_SECONDS;
      FirstAid.painkillerActivationDelaySeconds = FirstAid.DEFAULT_PAINKILLER_ACTIVATION_DELAY_SECONDS;
      FirstAid.injuryDebuffMode = FirstAid.InjuryDebuffMode.NORMAL;
      FirstAid.lowInjuryDebuffDamageScale = 0.4F;
      FirstAid.lowInjuryDebuffAmplifierScale = 0.5F;
      FirstAid.lowInjuryDebuffDurationScale = 0.5F;
      FirstAid.injuryDebuffOverrides.clear();
      FirstAid.setSuppressionEntityBlacklist(FirstAid.getDefaultSuppressionEntityBlacklist());
      CapProvider.tutorialDone.clear();
      hitList.clear();
      rescueProgress.clear();
      executionProgress.clear();
      FirstAidRegistryLookups.reset();
   }

   private static float totalPartHealth(AbstractPlayerDamageModel model) {
        float health = 0.0f;
        for (var part : model) health += part.currentHealth;
        return health;
    }

    private static void recordRecentPlayerAttack(LivingEntity victim, ServerPlayer attacker) {
        if (victim instanceof ServerPlayer target && target != attacker) {
            recentPlayerAttackers.computeIfAbsent(target, ignored -> new HashMap<>())
                .put(attacker.getUUID(), target.level().getGameTime());
        }
    }

    private static void tickEncounterAdrenaline(ServerPlayer player, PlayerDamageModel model) {
        if (model.isUnconscious() || player.level().getGameTime() % 20L != 0L) return;
        double threatRange = FirstAidConfig.SERVER.encounterThreatRange.get();
        if (FirstAid.projectileSuppressionEnabled) {
            double sightRange = FirstAidConfig.SERVER.encounterSightRange.get();
            boolean encounter = model.getSuppressionIntensity() <= 0.0f
                ? hasVisibleHostile(player, sightRange)
                : hasTargetingMob(player, threatRange) || hasRecentPlayerThreat(player, threatRange);
            if (encounter) model.ensureEncounterAdrenaline(player, FirstAidConfig.SERVER.encounterBaseIntensity.get().floatValue());
        }
        if (model.isAdrenalineFatigueReady() && !hasAdrenalineFatigueThreat(player, threatRange)) {
            model.applyAdrenalineFatigue(player);
        }
    }

    public static boolean hasAdrenalineFatigueThreat(ServerPlayer player, double range) {
        if (hasRecentPlayerThreat(player, range)) return true;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range))) {
            if (mob.isAlive() && mob.distanceToSqr(player) <= range * range
                && (mob instanceof Enemy || mob instanceof NeutralMob && mob.getTarget() == player)) return true;
        }
        return false;
    }

    public static boolean hasVisibleHostile(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0f);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range))) {
            if (!(mob instanceof Enemy) || !mob.isAlive() || mob.isInvisibleTo(player)) continue;
            Vec3 toward = mob.getEyePosition().subtract(eye);
            if (toward.lengthSqr() > range * range || toward.lengthSqr() < 1.0E-6D) continue;
            if (view.dot(toward.normalize()) >= Math.cos(Math.toRadians(50.0D)) && player.hasLineOfSight(mob)) return true;
        }
        return false;
    }

    private static boolean hasTargetingMob(ServerPlayer player, double range) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range))) {
            if (mob.isAlive() && mob.getTarget() == player && mob.distanceToSqr(player) <= range * range) return true;
        }
        return false;
    }

    private static boolean hasRecentPlayerThreat(ServerPlayer player, double range) {
        Map<UUID, Long> attacks = recentPlayerAttackers.get(player);
        if (attacks == null) return false;
        long now = player.level().getGameTime();
        int memory = FirstAidConfig.SERVER.encounterRecentAttackerTicks.get();
        attacks.entrySet().removeIf(entry -> now < entry.getValue() || now - entry.getValue() > memory);
        if (attacks.isEmpty()) {
            recentPlayerAttackers.remove(player);
            return false;
        }
        for (Player other : player.level().players()) {
            if (other != player && other.isAlive() && attacks.containsKey(other.getUUID())
                && other.distanceToSqr(player) <= range * range) return true;
        }
        return false;
    }

    public static boolean isMovingProjectile(Projectile projectile) {
        return projectile.getDeltaMovement().lengthSqr() >= 0.02D
            && projectile.position().distanceToSqr(new Vec3(projectile.xo, projectile.yo, projectile.zo)) >= 0.02D;
    }

    private static float getNearbyProjectileStrength(Player player) {
      if (!FirstAid.projectileSuppressionEnabled) {
         return 0.0F;
      }

      AABB scanBox = player.getBoundingBox().inflate(3.25);
      AABB playerBox = player.getBoundingBox().inflate(0.12);
      Vec3 eyePosition = player.getEyePosition();
      Vec3 torsoPosition = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
      float strongest = 0.0F;

      for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, scanBox, projectilex -> {
         if (!projectilex.isAlive() || projectilex.getOwner() == player) {
            return false;
         } else {
            return FirstAid.isSuppressionBlacklisted(projectilex) ? false : isMovingProjectile(projectilex);
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

   /**
    * Stops vanilla and ability-mod mobility while the player is downed.
    * During the critical crawl window, allows slow horizontal movement only.
    */
   private static void restrictUnconsciousMovement(Player player, PlayerDamageModel playerDamageModel) {
      player.setSprinting(false);
      player.setJumping(false);
      Vec3 motion = player.getDeltaMovement();
      double y = Math.min(0.0D, motion.y);
      if (playerDamageModel.canCrawlWhileDowned()) {
         double maxHorizontal = 0.10D * playerDamageModel.getCrawlSpeedFactor() / 0.28D;
         maxHorizontal = Math.max(0.06D, Math.min(0.12D, maxHorizontal));
         Vec3 projected = projectForwardCrawlMotion(player, motion.x, y, motion.z, maxHorizontal);
         if (projected.x != motion.x || projected.z != motion.z || motion.y > 0.0D) {
            player.setDeltaMovement(projected);
         }
         player.xxa = 0.0F;
         if (player.zza < 0.0F) {
            player.zza = 0.0F;
         }
         player.yya = 0.0F;
         player.syncVelocity = true;
         return;
      }
      if (motion.x != 0.0D || motion.z != 0.0D || motion.y > 0.0D) {
         player.setDeltaMovement(0.0D, y, 0.0D);
      }
      player.xxa = 0.0F;
      player.zza = 0.0F;
      player.yya = 0.0F;
      player.syncVelocity = true;
   }

   private static Vec3 projectForwardCrawlMotion(Player player, double motionX, double motionY, double motionZ, double maxHorizontal) {
      Vec3 look = player.getLookAngle();
      double fx = look.x;
      double fz = look.z;
      double flen = Math.sqrt(fx * fx + fz * fz);
      if (flen < 1.0E-4D) {
         fx = 0.0D;
         fz = 1.0D;
         flen = 1.0D;
      }
      fx /= flen;
      fz /= flen;
      double along = motionX * fx + motionZ * fz;
      if (along < 0.0D) {
         along = 0.0D;
      }
      if (along > maxHorizontal) {
         along = maxHorizontal;
      }
      return new Vec3(fx * along, motionY, fz * along);
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
      if (FirstAidConfig.SERVER.commandTipsEnabled.get() && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
         player.sendSystemMessage(Component.translatable("firstaid.tip.commands.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
         player.sendSystemMessage(Component.translatable("firstaid.tip.commands.subheader").withStyle(ChatFormatting.GRAY), false);
         player.sendSystemMessage(
            buildCommandTipLine(
               "firstaid.tip.commands.group.core",
               buildCommandTipChip("firstaid.tip.commands.pain.label", "firstaid.tip.commands.pain.detail", "/firstaid pain mild", ChatFormatting.AQUA),
               buildCommandTipChip("firstaid.tip.commands.suppression.label", "firstaid.tip.commands.suppression.detail", "/firstaid adrenaline dynamic", ChatFormatting.AQUA),
               buildCommandTipChip("firstaid.tip.commands.commandtips.label", "firstaid.tip.commands.commandtips.detail", "/firstaid commandtips off", ChatFormatting.GRAY),
               buildCommandTipChip("firstaid.tip.commands.medicineeffect.label", "firstaid.tip.commands.medicineeffect.detail", "/firstaid medicineeffect assisted", ChatFormatting.YELLOW)
            ),
            false
         );
         player.sendSystemMessage(
            buildCommandTipLine(
               "firstaid.tip.commands.group.rescue",
               buildCommandTipChip("firstaid.tip.commands.naturalregen.label", "firstaid.tip.commands.naturalregen.detail", "/firstaid naturalregen limited critical", ChatFormatting.GREEN),
               buildCommandTipChip("firstaid.tip.commands.revivewakeup.label", "firstaid.tip.commands.revivewakeup.detail", "/firstaid revivewakeup on 15", ChatFormatting.GREEN)
            ),
            false
         );
         player.sendSystemMessage(
            buildCommandTipLine(
               "firstaid.tip.commands.group.advanced",
               buildCommandTipChip("firstaid.tip.commands.injurydebuff.label", "firstaid.tip.commands.injurydebuff.detail", "/firstaid injurydebuff normal", ChatFormatting.GOLD),
               buildCommandTipChip("firstaid.tip.commands.randomdamage.label", "firstaid.tip.commands.randomdamage.detail", "/firstaid randomdamage friendly chance 80", ChatFormatting.GOLD),
               buildCommandTipChip("firstaid.tip.commands.addiction.label", "firstaid.tip.commands.addiction.detail", "/firstaid addiction set @s 0", ChatFormatting.LIGHT_PURPLE),
                buildCommandTipChip("firstaid.tip.commands.damagepart.label", "firstaid.tip.commands.damagepart.detail", "/damagePart HEAD 4", ChatFormatting.RED)
            ),
            false
         );
      }
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
      chip.append(
         Component.translatable(labelKey).withStyle(style -> style
            .withColor(color)
            .withBold(true)
            .withClickEvent(new ClickEvent.SuggestCommand(suggestion))
            .withHoverEvent(new HoverEvent.ShowText(Component.translatable(detailKey))))
      );
      chip.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
      return chip;
   }

   private static void tickRescueProgress(ServerPlayer rescuer) {
      EventHandler.InteractionTarget rescueTarget = findRescueTarget(rescuer, true);
      if (rescueTarget == null) {
         rescueProgress.remove(rescuer.getUUID());
      } else {
         ItemStack rescueStack = rescuer.getItemInHand(rescueTarget.hand());
         int rescueDurationTicks = getRescueDurationTicks(rescueStack);
         EventHandler.RescueProgress progress = rescueProgress.get(rescuer.getUUID());
         if (progress == null || !progress.matches(rescueTarget, rescueDurationTicks)) {
            progress = new EventHandler.RescueProgress(rescueTarget.target().getUUID(), rescueTarget.hand(), 0, rescueDurationTicks);
         }

         int nextTicks = Math.min(rescueDurationTicks, progress.ticks() + 1);
         if (nextTicks < rescueDurationTicks) {
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
         boolean usingDefibrillator = isDefibrillator(stack);
        boolean usingAdrenaline = isAdrenalineInjector(stack);
         if (usingDefibrillator || usingAdrenaline && stack.isDamageableItem()) {
            stack.hurtAndBreak(1, rescuer, getEquipmentSlot(rescueTarget.hand()));
         } else {
            stack.shrink(1);
         }

         boolean rescued = usingDefibrillator
            ? playerDamageModel.defibrillatorRescueFromCriticalState(rescueTarget.target(), FirstAid.rescueWakeUpEnabled)
            : usingAdrenaline
                ? playerDamageModel.adrenalineRescueFromCriticalState(rescueTarget.target(), FirstAid.rescueWakeUpEnabled)
                : playerDamageModel.rescueFromCriticalState(rescueTarget.target(), null, FirstAid.rescueWakeUpEnabled);
         if (rescued) {
            sendActionBarMessage(rescuer, Component.translatable("firstaid.gui.rescue_other", new Object[]{rescueTarget.target().getDisplayName()}).withStyle(ChatFormatting.GREEN));
            sendActionBarMessage(rescueTarget.target(), Component.translatable("firstaid.gui.rescue_received", new Object[]{rescuer.getDisplayName()}).withStyle(ChatFormatting.GREEN));
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

         sendActionBarMessage(executor, Component.translatable("firstaid.gui.execute_other", new Object[]{executionTarget.target().getDisplayName()}).withStyle(ChatFormatting.RED));
         sendActionBarMessage(executionTarget.target(), Component.translatable("firstaid.gui.execute_received", new Object[]{executor.getDisplayName()}).withStyle(ChatFormatting.RED));
         playerDamageModel.giveUp(executionTarget.target());
         executionProgress.remove(executor.getUUID());
      } else {
         executionProgress.remove(executor.getUUID());
      }
   }

   private static void sendActionBarMessage(Player player, Component message) {
      if (player instanceof ServerPlayer serverPlayer) {
         serverPlayer.sendSystemMessage(message, true);
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
      return stack.is(RegistryObjects.BANDAGE.get()) || stack.is(RegistryObjects.PLASTER.get()) || isDefibrillator(stack) || isAdrenalineInjector(stack);
   }
    private static boolean isAdrenalineInjector(ItemStack stack) {
        return stack.is(RegistryObjects.ADRENALINE_INJECTOR.get());
    }


   private static boolean isDefibrillator(ItemStack stack) {
      return stack.is(RegistryObjects.DEFIBRILLATOR.get());
   }

   private static int getRescueDurationTicks(ItemStack stack) {
      return isDefibrillator(stack) || isAdrenalineInjector(stack) ? DEFIBRILLATOR_RESCUE_DURATION_TICKS : RESCUE_DURATION_TICKS;
   }

   private static EquipmentSlot getEquipmentSlot(InteractionHand hand) {
      return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
   }

   private record ProjectileHitContext(Entity projectile, Vec3 hitPosition) {
   }

   private record ClosestPointResult(Vec3 point, double progress) {
   }

   private record RescueProgress(UUID targetId, InteractionHand hand, int ticks, int durationTicks) {
      private boolean matches(EventHandler.InteractionTarget rescueTarget, int rescueDurationTicks) {
         return this.targetId.equals(rescueTarget.target().getUUID()) && this.hand == rescueTarget.hand() && this.durationTicks == rescueDurationTicks;
      }

      private EventHandler.RescueProgress withTicks(int updatedTicks) {
         return new EventHandler.RescueProgress(this.targetId, this.hand, updatedTicks, this.durationTicks);
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


    public static void attemptSelfDefibrillator(ServerPlayer player) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
            return;
        }
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!isDefibrillator(stack)) {
            hand = InteractionHand.OFF_HAND;
            stack = player.getItemInHand(hand);
        }
        if (!isDefibrillator(stack)) {
            return;
        }
        stack.hurtAndBreak(1, player, getEquipmentSlot(hand));
        boolean rescued = playerDamageModel.defibrillatorRescueFromCriticalState(player, FirstAid.rescueWakeUpEnabled);
        if (rescued) {
            sendActionBarMessage(player, Component.translatable("firstaid.gui.self_defib_success").withStyle(ChatFormatting.GREEN));
        }
    }

   private static enum InteractionType {
      RESCUE,
      EXECUTE;
   }
}
