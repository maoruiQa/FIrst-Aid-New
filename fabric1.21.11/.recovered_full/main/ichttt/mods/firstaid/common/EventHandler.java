/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
 *  net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
 *  net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
 *  net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
 *  net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
 *  net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
 *  net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
 *  net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
 *  net.fabricmc.fabric.api.event.player.AttackBlockCallback
 *  net.fabricmc.fabric.api.event.player.AttackEntityCallback
 *  net.fabricmc.fabric.api.event.player.UseBlockCallback
 *  net.fabricmc.fabric.api.event.player.UseEntityCallback
 *  net.fabricmc.fabric.api.event.player.UseItemCallback
 *  net.fabricmc.fabric.api.loot.v3.LootTableEvents
 *  net.fabricmc.fabric.api.loot.v3.LootTableSource
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.ChatFormatting
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.Identifier
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.server.permissions.Permissions
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.tags.DamageTypeTags
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.damagesource.DamageTypes
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityDimensions
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.ai.Brain
 *  net.minecraft.world.entity.ai.memory.MemoryModuleType
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.entity.projectile.Projectile
 *  net.minecraft.world.entity.projectile.arrow.AbstractArrow
 *  net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.crafting.Recipe
 *  net.minecraft.world.item.crafting.RecipeManager
 *  net.minecraft.world.level.ClipContext
 *  net.minecraft.world.level.ClipContext$Block
 *  net.minecraft.world.level.ClipContext$Fluid
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.gamerules.GameRules
 *  net.minecraft.world.level.storage.loot.BuiltInLootTables
 *  net.minecraft.world.level.storage.loot.LootPool
 *  net.minecraft.world.level.storage.loot.LootPool$Builder
 *  net.minecraft.world.level.storage.loot.LootTable
 *  net.minecraft.world.level.storage.loot.LootTable$Builder
 *  net.minecraft.world.level.storage.loot.entries.LootItem
 *  net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer$Builder
 *  net.minecraft.world.level.storage.loot.functions.LootItemFunction$Builder
 *  net.minecraft.world.level.storage.loot.functions.SetItemCountFunction
 *  net.minecraft.world.level.storage.loot.providers.number.ConstantValue
 *  net.minecraft.world.level.storage.loot.providers.number.NumberProvider
 *  net.minecraft.world.level.storage.loot.providers.number.UniformGenerator
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.EntityHitResult
 *  net.minecraft.world.phys.HitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 *  org.apache.commons.lang3.tuple.Pair
 */
package ichttt.mods.firstaid.common;

import com.mojang.brigadier.CommandDispatcher;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.DebugDamageCommand;
import ichttt.mods.firstaid.common.FirstAidCommand;
import ichttt.mods.firstaid.common.FirstAidDamageModelHolder;
import ichttt.mods.firstaid.common.RegistryObjects;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

public final class EventHandler {
    public static final Random RAND = new Random();
    private static final EntityDimensions PLAYER_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable((float)1.4f, (float)0.4f);
    private static final List<ResourceKey<Recipe<?>>> STARTER_RECIPES = List.of(EventHandler.recipeKey("bandage"), EventHandler.recipeKey("plaster"), EventHandler.recipeKey("morphine"), EventHandler.recipeKey("painkillers"));
    private static final int RESCUE_DURATION_TICKS = PlayerDamageModel.getRescueDurationTicks();
    public static final Map<Player, Pair<Entity, HitResult>> hitList = new WeakHashMap<Player, Pair<Entity, HitResult>>();
    private static final Map<UUID, RescueProgress> rescueProgress = new HashMap<UUID, RescueProgress>();

    private EventHandler() {
    }

    public static void registerServerEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EventHandler::onAllowDamage);
        ServerTickEvents.END_WORLD_TICK.register(EventHandler::tickPlayers);
        EntitySleepEvents.STOP_SLEEPING.register(EventHandler::onStopSleeping);
        LootTableEvents.MODIFY.register(EventHandler::onLootTableModify);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DebugDamageCommand.register((CommandDispatcher<CommandSourceStack>)dispatcher);
            FirstAidCommand.register((CommandDispatcher<CommandSourceStack>)dispatcher);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> EventHandler.onLogin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EventHandler.onLogout(handler.getPlayer()));
        ServerPlayerEvents.COPY_FROM.register(EventHandler::onCopyFrom);
        ServerPlayerEvents.AFTER_RESPAWN.register(EventHandler::onAfterRespawn);
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> EventHandler.onDimensionChange(player));
        ServerWorldEvents.LOAD.register((server, world) -> EventHandler.onWorldLoad(world));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FirstAidConfig.applyCommandSettings();
            FirstAidRegistryLookups.init((HolderLookup.Provider)server.registryAccess(), false);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                FirstAidRegistryLookups.init((HolderLookup.Provider)server.registryAccess(), false);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> EventHandler.onServerStop());
        UseEntityCallback.EVENT.register(EventHandler::onEntityInteract);
        UseItemCallback.EVENT.register(EventHandler::onItemUse);
        UseBlockCallback.EVENT.register(EventHandler::onBlockInteract);
        AttackEntityCallback.EVENT.register(EventHandler::onAttackEntity);
        AttackBlockCallback.EVENT.register(EventHandler::onBlockAttack);
    }

    public static void recordProjectileHit(Player player, Entity projectile, HitResult hitResult) {
        hitList.put(player, (Pair<Entity, HitResult>)Pair.of((Object)projectile, (Object)hitResult));
    }

    private static ResourceKey<Recipe<?>> recipeKey(String path) {
        return ResourceKey.create((ResourceKey)Registries.RECIPE, (Identifier)Identifier.fromNamespaceAndPath((String)"firstaid", (String)path));
    }

    private static void awardStarterRecipes(ServerPlayer player) {
        List recipes = STARTER_RECIPES.stream().map(arg_0 -> ((RecipeManager)Objects.requireNonNull(player.level().getServer()).getRecipeManager()).byKey(arg_0)).flatMap(Optional::stream).toList();
        if (!recipes.isEmpty()) {
            player.awardRecipes(recipes);
        }
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide() || !CommonUtils.hasDamageModel((Entity)entity)) {
            return true;
        }
        Player player = (Player)entity;
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return true;
        }
        if (EventHandler.isProtectedUnconsciousSuffocation(damageModel, source)) {
            hitList.remove(player);
            return false;
        }
        if (amount == Float.MAX_VALUE || Float.isNaN(amount) || amount == Float.POSITIVE_INFINITY) {
            damageModel.forEach(damageablePart -> {
                damageablePart.currentHealth = 0.0f;
            });
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            }
            CommonUtils.killPlayer(damageModel, player, source);
            return false;
        }
        boolean addStat = amount < 3.4028235E37f;
        IDamageDistributionAlgorithm damageDistribution = FirstAidRegistryLookups.getDamageDistributions(source.type());
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            Entity entityProjectile;
            EquipmentSlot slot;
            Pair<Entity, HitResult> rayTraceResult;
            AbstractArrow arrow;
            Entity directEntity = source.getDirectEntity();
            if (directEntity instanceof AbstractArrow && (arrow = (AbstractArrow)directEntity).getPierceLevel() == 0) {
                arrow.discard();
            }
            if ((rayTraceResult = hitList.remove(player)) != null && (slot = PlayerSizeHelper.getSlotTypeForProjectileHit(entityProjectile = (Entity)rayTraceResult.getLeft(), player)) != null) {
                List<EnumPlayerPart> possibleParts = CommonUtils.getPartListForSlot(slot);
                damageDistribution = new StandardDamageDistributionAlgorithm(Collections.singletonMap(slot, possibleParts), false, true);
            }
        }
        if (damageDistribution == null && (damageDistribution = PlayerSizeHelper.getMeleeDistribution(player, source)) == null) {
            damageDistribution = RandomDamageDistributionAlgorithm.getDefault();
        }
        DamageDistribution.handleDamageTaken(damageDistribution, damageModel, amount, player, source, addStat, true);
        if (amount > 0.0f && player.isAlive()) {
            float pitch = 0.9f + player.level().random.nextFloat() * 0.2f;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, player.getSoundSource(), 1.0f, pitch);
        }
        hitList.remove(player);
        return false;
    }

    private static void tickPlayers(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            AbstractPlayerDamageModel damageModel;
            if (player.getAbilities().invulnerable || !player.isAlive() || (damageModel = CommonUtils.getDamageModel((Player)player)) == null) continue;
            if (damageModel instanceof PlayerDamageModel) {
                PlayerDamageModel playerDamageModel = (PlayerDamageModel)damageModel;
                float nearMissStrength = EventHandler.getNearbyProjectileStrength((Player)player);
                if (nearMissStrength > 0.0f) {
                    playerDamageModel.registerAdrenalineNearMiss(nearMissStrength);
                }
                if (playerDamageModel.isUnconscious()) {
                    EventHandler.clearAttackTargetsAround((LivingEntity)player, 24.0);
                }
            }
            damageModel.tick((Level)player.level(), (Player)player);
            EventHandler.tickRescueProgress(player);
            hitList.remove(player);
        }
    }

    private static void onStopSleeping(LivingEntity entity, BlockPos sleepingPos) {
        if (entity.level().isClientSide() || !(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
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

    private static void onLootTableModify(ResourceKey<LootTable> key, LootTable.Builder tableBuilder, LootTableSource source, HolderLookup.Provider registries) {
        int morphine;
        int plaster;
        int bandage;
        UniformGenerator bandageMax = UniformGenerator.between((float)1.0f, (float)3.0f);
        UniformGenerator plasterMax = UniformGenerator.between((float)1.0f, (float)5.0f);
        UniformGenerator morphineMax = UniformGenerator.between((float)1.0f, (float)2.0f);
        ConstantValue poolRolls = ConstantValue.exactly((float)1.0f);
        if (key.equals((Object)BuiltInLootTables.SPAWN_BONUS_CHEST)) {
            bandage = 8;
            plaster = 16;
            morphine = 4;
            morphineMax = ConstantValue.exactly((float)1.0f);
        } else if (key.equals((Object)BuiltInLootTables.STRONGHOLD_CORRIDOR) || key.equals((Object)BuiltInLootTables.STRONGHOLD_CROSSING) || key.equals((Object)BuiltInLootTables.ABANDONED_MINESHAFT)) {
            bandage = 20;
            plaster = 24;
            morphine = 8;
            poolRolls = UniformGenerator.between((float)0.0f, (float)1.0f);
        } else if (key.equals((Object)BuiltInLootTables.VILLAGE_BUTCHER)) {
            bandage = 4;
            plaster = 20;
            morphine = 2;
            plasterMax = UniformGenerator.between((float)3.0f, (float)8.0f);
        } else if (key.equals((Object)BuiltInLootTables.IGLOO_CHEST)) {
            bandage = 4;
            plaster = 8;
            morphine = 2;
            poolRolls = UniformGenerator.between((float)0.0f, (float)1.0f);
        } else if (key.equals((Object)BuiltInLootTables.SHIPWRECK_SUPPLY)) {
            bandage = 4;
            plaster = 8;
            morphine = 2;
            bandageMax = UniformGenerator.between((float)1.0f, (float)2.0f);
            plasterMax = UniformGenerator.between((float)1.0f, (float)3.0f);
            morphineMax = ConstantValue.exactly((float)1.0f);
            poolRolls = UniformGenerator.between((float)0.0f, (float)1.0f);
        } else {
            return;
        }
        LootPool.Builder builder = LootPool.lootPool().setRolls((NumberProvider)poolRolls);
        builder.add((LootPoolEntryContainer.Builder)LootItem.lootTableItem((ItemLike)((ItemLike)RegistryObjects.BANDAGE.get())).apply((LootItemFunction.Builder)SetItemCountFunction.setCount((NumberProvider)bandageMax)).setWeight(bandage).setQuality(0));
        builder.add((LootPoolEntryContainer.Builder)LootItem.lootTableItem((ItemLike)((ItemLike)RegistryObjects.PLASTER.get())).apply((LootItemFunction.Builder)SetItemCountFunction.setCount((NumberProvider)plasterMax)).setWeight(plaster).setQuality(0));
        builder.add((LootPoolEntryContainer.Builder)LootItem.lootTableItem((ItemLike)((ItemLike)RegistryObjects.MORPHINE.get())).apply((LootItemFunction.Builder)SetItemCountFunction.setCount((NumberProvider)morphineMax)).setWeight(morphine).setQuality(0));
        tableBuilder.withPool(builder);
    }

    private static InteractionResult onEntityInteract(Player rescuer, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
        if (level.isClientSide()) {
            if (EventHandler.isUnconscious(rescuer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        if (EventHandler.isUnconscious(rescuer)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult onItemUse(Player player, Level level, InteractionHand hand) {
        return EventHandler.cancelIfUnconscious(player);
    }

    private static InteractionResult onBlockInteract(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        return EventHandler.cancelIfUnconscious(player);
    }

    private static InteractionResult onBlockAttack(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
        return EventHandler.cancelIfUnconscious(player);
    }

    private static InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
        return EventHandler.cancelIfUnconscious(player);
    }

    private static void onLogin(ServerPlayer player) {
        if (player.level().isClientSide()) {
            return;
        }
        FirstAid.LOGGER.debug("Sending damage model to {}", (Object)player.getName());
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)player);
        if (damageModel == null) {
            return;
        }
        if (damageModel.hasTutorial) {
            CapProvider.tutorialDone.add(player.getName().getString());
        }
        EventHandler.awardStarterRecipes(player);
        FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        FirstAidNetworking.sendServerConfig(player);
        EventHandler.sendOpCommandTip(player);
    }

    private static void onLogout(ServerPlayer player) {
        hitList.remove(player);
        rescueProgress.remove(player.getUUID());
    }

    private static void onWorldLoad(ServerLevel world) {
        world.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, (Object)FirstAidConfig.SERVER.allowNaturalRegeneration.get(), world.getServer());
    }

    private static void onDimensionChange(ServerPlayer player) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)player);
        if (damageModel == null) {
            return;
        }
        FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        FirstAidNetworking.sendServerConfig(player);
    }

    private static void onCopyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        AbstractPlayerDamageModel oldModel = CommonUtils.getExistingDamageModel((Player)oldPlayer);
        if (oldModel instanceof PlayerDamageModel) {
            PlayerDamageModel oldDamageModel = (PlayerDamageModel)oldModel;
            if (newPlayer instanceof FirstAidDamageModelHolder) {
                FirstAidDamageModelHolder holder = (FirstAidDamageModelHolder)newPlayer;
                PlayerDamageModel cloned = new PlayerDamageModel();
                cloned.deserializeNBT(oldDamageModel.serializeNBT());
                holder.firstaid$setDamageModel(cloned);
            }
        }
    }

    private static void onAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (alive) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)newPlayer);
        if (damageModel == null) {
            return;
        }
        damageModel.runScaleLogic((Player)newPlayer);
        damageModel.forEach(damageablePart -> damageablePart.heal(damageablePart.getMaxHealth(), (Player)newPlayer, false));
        if (damageModel instanceof PlayerDamageModel) {
            PlayerDamageModel playerDamageModel = (PlayerDamageModel)damageModel;
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
        AABB scanBox = player.getBoundingBox().inflate(3.25);
        AABB playerBox = player.getBoundingBox().inflate(0.12);
        Vec3 eyePosition = player.getEyePosition();
        Vec3 torsoPosition = player.position().add(0.0, (double)player.getBbHeight() * 0.6, 0.0);
        float strongest = 0.0f;
        List projectiles = player.level().getEntitiesOfClass(Projectile.class, scanBox, projectile -> {
            if (!projectile.isAlive() || projectile.getOwner() == player) {
                return false;
            }
            if (projectile instanceof AbstractThrownPotion) {
                return false;
            }
            return projectile.getDeltaMovement().lengthSqr() >= 0.02;
        });
        for (Projectile projectile2 : projectiles) {
            Vec3 currentPosition = projectile2.position();
            Vec3 previousPosition = currentPosition.subtract(projectile2.getDeltaMovement());
            Vec3 endPosition = currentPosition.add(projectile2.getDeltaMovement());
            if (playerBox.intersects(projectile2.getBoundingBox()) || playerBox.clip(previousPosition, endPosition).isPresent()) continue;
            strongest = Math.max(strongest, EventHandler.getNearMissStrength(player, projectile2, previousPosition, endPosition, eyePosition));
            strongest = Math.max(strongest, EventHandler.getNearMissStrength(player, projectile2, previousPosition, endPosition, torsoPosition));
        }
        return strongest;
    }

    private static float getNearMissStrength(Player player, Projectile projectile, Vec3 start, Vec3 end, Vec3 target) {
        ClosestPointResult closestPointResult = EventHandler.closestPointOnSegment(start, end, target);
        if (closestPointResult.progress <= 0.0 || closestPointResult.progress >= 1.0) {
            return 0.0f;
        }
        double distance = closestPointResult.point.distanceTo(target);
        if (distance > 1.85) {
            return 0.0f;
        }
        BlockHitResult hitResult = player.level().clip(new ClipContext(closestPointResult.point, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)player));
        if (hitResult.getType() != HitResult.Type.MISS) {
            return 0.0f;
        }
        double speed = projectile.getDeltaMovement().length();
        float speedFactor = Mth.clamp((float)((float)((speed - 0.18) / 0.65)), (float)0.0f, (float)1.0f);
        float distanceFactor = Mth.clamp((float)(1.32f - (float)(distance / 1.85)), (float)0.0f, (float)1.0f);
        return Mth.clamp((float)(distanceFactor * (0.82f + 0.58f * speedFactor)), (float)0.0f, (float)1.45f);
    }

    private static ClosestPointResult closestPointOnSegment(Vec3 start, Vec3 end, Vec3 target) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-7) {
            return new ClosestPointResult(start, 0.0);
        }
        double progress = Mth.clamp((double)(target.subtract(start).dot(segment) / lengthSqr), (double)0.0, (double)1.0);
        return new ClosestPointResult(start.add(segment.scale(progress)), progress);
    }

    private static InteractionResult cancelIfUnconscious(Player player) {
        return EventHandler.isUnconscious(player) ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    private static boolean isUnconscious(Player player) {
        return EventHandler.isUnconscious(player, true);
    }

    private static boolean isUnconscious(Player player, boolean allowCreate) {
        PlayerDamageModel playerDamageModel;
        AbstractPlayerDamageModel damageModel = allowCreate ? CommonUtils.getDamageModel(player) : CommonUtils.getExistingDamageModel(player);
        return damageModel instanceof PlayerDamageModel && (playerDamageModel = (PlayerDamageModel)damageModel).isUnconscious();
    }

    private static boolean isProtectedUnconsciousSuffocation(AbstractPlayerDamageModel damageModel, DamageSource source) {
        PlayerDamageModel playerDamageModel;
        return damageModel instanceof PlayerDamageModel && (playerDamageModel = (PlayerDamageModel)damageModel).isUnconscious() && source.is(DamageTypes.IN_WALL);
    }

    private static void clearAttackTargetsAround(LivingEntity victim, double range) {
        List mobs = victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(range));
        for (Mob mob : mobs) {
            if (mob.getTarget() != victim) continue;
            mob.setTarget(null);
            Brain brain = mob.getBrain();
            EventHandler.eraseMemory(brain, MemoryModuleType.ANGRY_AT);
            EventHandler.eraseMemory(brain, MemoryModuleType.ATTACK_TARGET);
        }
    }

    private static void eraseMemory(Brain<?> brain, MemoryModuleType<?> type) {
        if (brain.hasMemoryValue(type)) {
            brain.eraseMemory(type);
        }
    }

    private static void sendOpCommandTip(ServerPlayer player) {
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }
        player.displayClientMessage((Component)Component.translatable((String)"firstaid.tip.commands.header").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), false);
        player.displayClientMessage((Component)Component.translatable((String)"firstaid.tip.commands.pain").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage((Component)Component.translatable((String)"firstaid.tip.commands.suppression").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage((Component)Component.translatable((String)"firstaid.tip.commands.medicineeffect").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage((Component)Component.translatable((String)"firstaid.tip.commands.injurydebuff").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage((Component)Component.translatable((String)"firstaid.tip.commands.revivewakeup").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage((Component)Component.translatable((String)"firstaid.tip.commands.damagepart").withStyle(ChatFormatting.YELLOW), false);
    }

    private static void tickRescueProgress(ServerPlayer rescuer) {
        int nextTicks;
        RescueTarget rescueTarget = EventHandler.findRescueTarget((Player)rescuer, true);
        if (rescueTarget == null) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }
        RescueProgress progress = rescueProgress.get(rescuer.getUUID());
        if (progress == null || !progress.matches(rescueTarget)) {
            progress = new RescueProgress(rescueTarget.target().getUUID(), rescueTarget.hand(), 0);
        }
        if ((nextTicks = Math.min(RESCUE_DURATION_TICKS, progress.ticks() + 1)) < RESCUE_DURATION_TICKS) {
            rescueProgress.put(rescuer.getUUID(), progress.withTicks(nextTicks));
            return;
        }
        EventHandler.completeRescue(rescuer, rescueTarget);
    }

    public static void attemptImmediateRescue(ServerPlayer rescuer) {
        RescueTarget rescueTarget = EventHandler.findRescueTarget((Player)rescuer, true);
        if (rescueTarget == null) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }
        EventHandler.completeRescue(rescuer, rescueTarget);
    }

    private static void completeRescue(ServerPlayer rescuer, RescueTarget rescueTarget) {
        PlayerDamageModel playerDamageModel;
        ItemStack stack = rescuer.getItemInHand(rescueTarget.hand());
        if (!EventHandler.isRescueItem(stack)) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(rescueTarget.target());
        if (!(damageModel instanceof PlayerDamageModel) || !(playerDamageModel = (PlayerDamageModel)damageModel).canBeRescued()) {
            rescueProgress.remove(rescuer.getUUID());
            return;
        }
        stack.shrink(1);
        if (playerDamageModel.rescueFromCriticalState(rescueTarget.target(), null, FirstAid.rescueWakeUpEnabled)) {
            rescuer.displayClientMessage((Component)Component.translatable((String)"firstaid.gui.rescue_other", (Object[])new Object[]{rescueTarget.target().getDisplayName()}).withStyle(ChatFormatting.GREEN), true);
            rescueTarget.target().displayClientMessage((Component)Component.translatable((String)"firstaid.gui.rescue_received", (Object[])new Object[]{rescuer.getDisplayName()}).withStyle(ChatFormatting.GREEN), true);
        }
        rescueProgress.remove(rescuer.getUUID());
    }

    private static RescueTarget findRescueTarget(Player rescuer, boolean requireSneaking) {
        if (rescuer == null || rescuer.level().isClientSide() || EventHandler.isUnconscious(rescuer)) {
            return null;
        }
        if (requireSneaking && !rescuer.isCrouching()) {
            return null;
        }
        InteractionHand hand = EventHandler.getRescueHand(rescuer);
        if (hand == null) {
            return null;
        }
        double maxDistanceSqr = PlayerDamageModel.getRescueRange() * PlayerDamageModel.getRescueRange();
        Player closestTarget = null;
        double closestDistanceSqr = maxDistanceSqr;
        for (Player candidate : rescuer.level().players()) {
            double distanceSqr;
            PlayerDamageModel playerDamageModel;
            AbstractPlayerDamageModel damageModel;
            if (candidate == rescuer || !candidate.isAlive() || !((damageModel = CommonUtils.getDamageModel(candidate)) instanceof PlayerDamageModel) || !(playerDamageModel = (PlayerDamageModel)damageModel).canBeRescued() || (distanceSqr = rescuer.distanceToSqr((Entity)candidate)) > closestDistanceSqr) continue;
            closestDistanceSqr = distanceSqr;
            closestTarget = candidate;
        }
        return closestTarget == null ? null : new RescueTarget(closestTarget, hand);
    }

    private static InteractionHand getRescueHand(Player player) {
        if (EventHandler.isRescueItem(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (EventHandler.isRescueItem(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static boolean isRescueItem(ItemStack stack) {
        return stack.is((Item)RegistryObjects.BANDAGE.get()) || stack.is((Item)RegistryObjects.PLASTER.get());
    }

    private record ClosestPointResult(Vec3 point, double progress) {
    }

    private record RescueTarget(Player target, InteractionHand hand) {
    }

    private record RescueProgress(UUID targetId, InteractionHand hand, int ticks) {
        private boolean matches(RescueTarget rescueTarget) {
            return this.targetId.equals(rescueTarget.target().getUUID()) && this.hand == rescueTarget.hand();
        }

        private RescueProgress withTicks(int updatedTicks) {
            return new RescueProgress(this.targetId, this.hand, updatedTicks);
        }
    }
}

