package ichttt.mods.firstaid.common.damagesystem;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.api.event.FirstAidLivingDamageEvent;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import net.neoforged.neoforge.common.NeoForge;
import java.util.function.Consumer;
import ichttt.mods.firstaid.common.damagesystem.debuff.ConstantDebuff;
import ichttt.mods.firstaid.common.damagesystem.debuff.ConstantDebuffEntry;
import ichttt.mods.firstaid.common.damagesystem.debuff.SharedDebuff;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import java.util.UUID;
import java.util.List;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;


public final class SuppressionRegressionGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(FirstAid.MODID, "suppression_regression");

    private SuppressionRegressionGameTests() {}

    public static void register(IEventBus bus) {
        DeferredRegister<MapCodec<? extends GameTestInstance>> types = DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, FirstAid.MODID);
        types.register("suppression_regression", () -> RegressionTest.CODEC);
        types.register(bus);
        bus.addListener(SuppressionRegressionGameTests::onRegister);
    }

    private static void onRegister(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(ID, new TestEnvironmentDefinition.AllOf());
        event.registerTest(ID, new RegressionTest(new TestData<>(environment, Identifier.parse("minecraft:empty"), 100, 0, true)));
    }

    private static final class RegressionTest extends GameTestInstance {
        private static final MapCodec<RegressionTest> CODEC = TestData.CODEC.xmap(RegressionTest::new, RegressionTest::info);
        private RegressionTest(TestData<Holder<TestEnvironmentDefinition<?>>> data) { super(data); }
        @Override public void run(GameTestHelper helper) { SuppressionRegressionGameTests.run(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("First Aid adrenaline regression"); }
    }

    private static void run(GameTestHelper helper) {
        testCombatAdrenalineLimits(helper);
        testAdrenalineReliefMultiplier(helper);
        float configuredRelief = FirstAid.adrenalineReliefMultiplier;
        try {
        FirstAid.adrenalineReliefMultiplier = 1.0F;
        var firstAidCommand = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("firstaid");
        helper.assertTrue(firstAidCommand != null && firstAidCommand.getChild("adrenaline") != null
            && firstAidCommand.getChild("suppression") != null, "new and legacy command names must coexist");
        helper.assertTrue(firstAidCommand.getChild("adrenaline").getChild("fatigue") != null
            && firstAidCommand.getChild("suppression").getChild("fatigue") != null,
            "fatigue settings must be available under both command names");
        helper.assertValueEqual(RegistryObjects.ADRENALINE_INJECTOR.get().getDefaultInstance().getMaxDamage(), 4,
            "adrenaline injector has four default uses");
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.removeAllEffects();
        float configuredGain = FirstAid.suppressionGainMultiplier;
        boolean configuredMild = FirstAid.lowSuppressionEnabled;
        FirstAid.suppressionGainMultiplier = 0.15F;
        PlayerDamageModel model = new PlayerDamageModel();
        model.registerAdrenalineNearMiss(player, 0.35F, 0);
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.0273F) < 0.0001F, "near miss growth must be halved after the base gain calculation");
        model.registerAdrenalineNearMiss(player, 0.35F, 0);
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - (0.0273F + 0.0273F * (1.0F - 0.0273F))) < 0.0001F,
            "growth must shrink with existing pressure");
        FirstAid.suppressionGainMultiplier = 0.24F;
        PlayerDamageModel tunedModel = new PlayerDamageModel();
        tunedModel.registerAdrenalineNearMiss(player, 0.35F, 0);
        helper.assertTrue(Math.abs(tunedModel.getSuppressionIntensity() - 0.04368F) < 0.0001F, "admin gain coefficient must change accumulation");
        FirstAid.suppressionGainMultiplier = configuredGain;
        float configuredDisplayScale = FirstAid.lowSuppressionMultiplier;
        FirstAid.lowSuppressionEnabled = false;
        helper.assertTrue(Math.abs(FirstAid.suppressionDisplayCurve(0.5F * FirstAid.getSuppressionDisplayMultiplier()) - 0.375F) < 0.0001F, "dynamic display uses seventy-five percent intensity");
        FirstAid.lowSuppressionEnabled = true;
        FirstAid.lowSuppressionMultiplier = 0.4F;
        helper.assertTrue(Math.abs(FirstAid.suppressionDisplayCurve(0.5F * FirstAid.getSuppressionDisplayMultiplier()) - 0.25F) < 0.0001F, "default mild display uses fifty percent intensity");
        FirstAid.lowSuppressionMultiplier = 0.0F;
        helper.assertTrue(FirstAid.getSuppressionDisplayMultiplier() == 0.0F, "custom zero display scale remains off");
        FirstAid.lowSuppressionMultiplier = 1.0F;
        helper.assertTrue(FirstAid.getSuppressionDisplayMultiplier() == 1.0F, "custom display scale stays bounded");
        FirstAid.lowSuppressionMultiplier = configuredDisplayScale;
        FirstAid.lowSuppressionEnabled = configuredMild;
        while (model.getSuppressionIntensity() > 0.0F) model.tickSuppressionState();
        helper.assertValueEqual(model.getSuppressionPainReliefTicks(), 0, "pressure must not leave a full-immunity grace period");
        helper.assertTrue(!model.isPainSuppressed(player), "pressure does not grant medicine-level pain suppression");

        model.applyTrackedInjuryEffect(player, "minecraft:slowness", new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
        helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "tracked injury effect must be applied");
        model.registerAdrenalineNearMiss(player, 0.35F, 0);
        helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "low pressure must not clear owned injury effects");
        model.applyTrackedInjuryEffect(player, "minecraft:slowness", new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
        helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "pressure must not skip injury effect application");

        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
        model.registerAdrenalineNearMiss(player, 0.35F, 0);
        helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "external same-type effect must survive");
        player.removeAllEffects();
        PlayerDamageModel overlapModel = new PlayerDamageModel();
        overlapModel.applyTrackedInjuryEffect(player, "minecraft:slowness", new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
        MobEffectInstance external = new MobEffectInstance(MobEffects.SLOWNESS, 400, 1);
        player.addEffect(external);
        overlapModel.onEffectAdded(player, external);
        overlapModel.registerAdrenalineNearMiss(player, 0.35F, 0);
        helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "ambiguous overlapping effect must survive");
        player.removeAllEffects();
        PlayerDamageModel medicineModel = (PlayerDamageModel) CommonUtils.getDamageModel(player);
        medicineModel.applyTrackedInjuryEffect(player, "minecraft:slowness", new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
        player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, 40, 0));
        helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "medicine effect event must clear owned injuries immediately");
        player.removeAllEffects();

        testInjuryDebuffRecovery(helper, player);
        testAdrenalineMechanics(helper, player);
        testAdrenalineThreatDecay(helper, player);
        testEncounterCrowdRecovery(helper, player);

        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 5.0D);
        player.setYRot(0.0F);
        player.setYHeadRot(0.0F);
        player.setXRot(0.0F);
        helper.assertTrue(EventHandler.hasVisibleHostile(player, 16.0D), "visible hostile in front starts encounter");
        player.setYRot(180.0F);
        player.setYHeadRot(180.0F);
        helper.assertTrue(!EventHandler.hasVisibleHostile(player, 16.0D), "hostile behind player is outside view");
        player.setYRot(0.0F);
        player.setYHeadRot(0.0F);
        BlockPos barrier = BlockPos.containing(zombie.getX(), player.getEyeY(), zombie.getZ() - 2.0D);
        helper.getLevel().setBlock(barrier, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!EventHandler.hasVisibleHostile(player, 16.0D), "occluded hostile cannot start encounter");
        helper.getLevel().setBlock(barrier, Blocks.AIR.defaultBlockState(), 3);
        player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 20.0D);
        helper.assertTrue(!EventHandler.hasVisibleHostile(player, 16.0D), "distant hostile is outside encounter range");
        player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 5.0D);
        var wolf = helper.spawn(EntityTypes.WOLF, new BlockPos(2, 1, 1));
        var cow = helper.spawn(EntityTypes.COW, new BlockPos(3, 1, 1));
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        helper.assertTrue(arrow != null, "arrow must spawn");
        arrow.setOwner(player);
        arrow.setDeltaMovement(new Vec3(0.5D, 0.0D, 0.0D));
        arrow.setPos(2.0D, 1.0D, 1.0D);
        arrow.xo = arrow.getX();
        arrow.yo = arrow.getY();
        arrow.zo = arrow.getZ();
        helper.assertTrue(!EventHandler.isMovingProjectile(arrow), "stationary arrow cannot cause a near miss");
        arrow.xo -= 0.5D;
        helper.assertTrue(EventHandler.isMovingProjectile(arrow), "flying arrow can cause a near miss");
        boolean enabled = FirstAid.projectileSuppressionEnabled;
        try {
            FirstAid.projectileSuppressionEnabled = true;
            var melee = helper.getLevel().damageSources().playerAttack(player);
            helper.assertTrue(EventHandler.isEligibleOffensiveDamage(zombie, melee, 2.0F), "enemy melee qualifies");
            PlayerDamageModel attackModel = (PlayerDamageModel) CommonUtils.getDamageModel(player);
            attackModel.clearStatusEffects();
            float beforeAttack = attackModel.getSuppressionIntensity();
            float savedAttackGain = FirstAid.suppressionGainMultiplier;
            FirstAid.suppressionGainMultiplier = 0.15F;
            zombie.hurtServer(helper.getLevel(), melee, 2.0F);
            helper.assertTrue(attackModel.getSuppressionIntensity() > beforeAttack, "actual enemy damage adds suppression");
            helper.assertTrue(Math.abs(attackModel.getSuppressionIntensity() - 0.06F) < 0.0001F, "melee default gain increases to six pressure points");
            FirstAid.suppressionGainMultiplier = savedAttackGain;
            helper.assertTrue(EventHandler.isEligibleOffensiveDamage(wolf, melee, 2.0F), "neutral melee qualifies");
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(cow, melee, 2.0F), "passive target excluded");
            FakePlayer otherPlayer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.fromString("f175a1d0-e14e-4b4a-b91a-2f06fa19c2ef"), "[FirstAidTarget]"));
            otherPlayer.setPos(player.getX() + 1.0D, player.getY(), player.getZ());
            if (!helper.getLevel().players().contains(player)) helper.getLevel().addNewPlayer(player);
            if (!helper.getLevel().players().contains(otherPlayer)) helper.getLevel().addNewPlayer(otherPlayer);
            zombie.setTarget(null);
            wolf.setTarget(null);
            helper.assertValueEqual(EventHandler.countEncounterThreats(otherPlayer, 12.0D), 0, "a nearby player who has not attacked is not a threat");
            helper.assertTrue(EventHandler.isEligibleOffensiveDamage(otherPlayer, melee, 2.0F), "other player target qualifies");
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(player, melee, 2.0F), "self damage excluded");
            float beforeSameTick = attackModel.getSuppressionIntensity();
            var sweepTarget = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
            helper.assertTrue(sweepTarget.hurtServer(helper.getLevel(), melee, 2.0F), "the second same-tick target takes the hit");
            sweepTarget.discard();
            helper.assertTrue(attackModel.getSuppressionIntensity() == beforeSameTick, "a second target hit in the same tick does not add adrenaline again");
            // The PvP hit below is a separate attack; a cleared state starts a new attack window.
            attackModel.clearStatusEffects();
            float beforePvP = attackModel.getSuppressionIntensity();
            PlayerDamageModel targetModel = (PlayerDamageModel) CommonUtils.getDamageModel(otherPlayer);
            targetModel.ensureEncounterAdrenaline(otherPlayer, 0.60F);
            float targetBeforePvP = targetModel.getSuppressionIntensity();
            helper.assertTrue(EventHandler.handleCustomPlayerDamage(otherPlayer, melee, 2.0F), "PvP body-part damage must be handled");
            helper.assertTrue(attackModel.getSuppressionIntensity() > beforePvP, "actual player damage adds adrenaline");
            helper.assertTrue(targetModel.getSuppressionIntensity() < targetBeforePvP, "actual PvP injury lowers victim adrenaline");
            helper.assertValueEqual(EventHandler.countEncounterThreats(otherPlayer, 12.0D), 1, "one recent player attacker counts once");
            var focusedPlayer = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            focusedPlayer.connection = player.connection;
            focusedPlayer.setPos(otherPlayer.getX(), otherPlayer.getY(), otherPlayer.getZ());
            helper.getLevel().addNewPlayer(focusedPlayer);
            zombie.setTarget(focusedPlayer);
            wolf.setTarget(focusedPlayer);
            helper.assertTrue(zombie.getTarget() == focusedPlayer && wolf.getTarget() == focusedPlayer, "mobs acquire a living survival target");
            helper.assertValueEqual(EventHandler.countEncounterThreats(focusedPlayer, 12.0D), 2, "two mobs targeting the same player count individually");
            wolf.setTarget(otherPlayer);
            helper.assertValueEqual(EventHandler.countEncounterThreats(focusedPlayer, 12.0D), 1, "a mob targeting somebody else is not counted");
            focusedPlayer.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 20.0D);
            helper.assertValueEqual(EventHandler.countEncounterThreats(focusedPlayer, 12.0D), 0, "distant targeting mobs are excluded");
            player.setPos(otherPlayer.getX() - 1.0D, otherPlayer.getY(), otherPlayer.getZ());
            zombie.setTarget(null);
            wolf.setTarget(null);
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(zombie, melee, 0.0F), "zero damage excluded");
            helper.assertTrue(EventHandler.isEligibleOffensiveDamage(zombie, helper.getLevel().damageSources().arrow(arrow, player), 2.0F), "owned projectile qualifies");
            var rangedTarget = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 1));
            attackModel.clearStatusEffects();
            float savedRangedGain = FirstAid.suppressionGainMultiplier;
            try {
                FirstAid.suppressionGainMultiplier = 0.15F;
                rangedTarget.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, player), 1.0F);
                helper.assertTrue(Math.abs(attackModel.getSuppressionIntensity() - 0.0546F) < 0.0001F, "ranged default gain remains unchanged");
            } finally {
                FirstAid.suppressionGainMultiplier = savedRangedGain;
            }
            arrow.setOwner(wolf);
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(zombie, helper.getLevel().damageSources().arrow(arrow, player), 2.0F), "unowned projectile excluded");
            float invalidAttackerGain = attackModel.getSuppressionIntensity();
            float invalidVictimGain = targetModel.getSuppressionIntensity();
            EventHandler.handleCustomPlayerDamage(otherPlayer, helper.getLevel().damageSources().arrow(arrow, player), 1.0F);
            helper.assertTrue(attackModel.getSuppressionIntensity() == invalidAttackerGain
                && targetModel.getSuppressionIntensity() < invalidVictimGain, "unowned projectile cannot reward attacker, but actual victim injury still lowers pressure");
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(zombie, helper.getLevel().damageSources().mobAttack(wolf), 2.0F), "non-player source excluded");
            FirstAid.projectileSuppressionEnabled = false;
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(zombie, melee, 2.0F), "suppression off excludes attack gain");
        } finally {
            FirstAid.projectileSuppressionEnabled = enabled;
        }
        PlayerDamageModel encounterModel = new PlayerDamageModel();
        encounterModel.ensureEncounterAdrenaline(player, 0.20F);
        helper.assertTrue(Math.abs(encounterModel.getSuppressionIntensity() - 0.20F) < 0.0001F, "encounter sets its minimum");
        encounterModel.ensureEncounterAdrenaline(player, 0.10F);
        helper.assertTrue(Math.abs(encounterModel.getSuppressionIntensity() - 0.20F) < 0.0001F, "encounter cannot lower accumulated adrenaline");
        player.removeAllEffects();
        encounterModel.ensureEncounterAdrenaline(player, 0.36F);
        encounterModel.tick(helper.getLevel(), player);
        helper.assertTrue(!player.hasEffect(MobEffects.HASTE), "natural adrenaline no longer grants Haste");
        helper.assertTrue(!player.hasEffect(MobEffects.STRENGTH), "medium pressure does not grant a separate vanilla Strength effect");
        encounterModel.ensureEncounterAdrenaline(player, 0.72F);
        encounterModel.tick(helper.getLevel(), player);
        helper.assertTrue(!player.hasEffect(MobEffects.STRENGTH), "pressure strength uses its own effect rather than vanilla Strength");
        player.addEffect(new MobEffectInstance(MobEffects.HASTE, 200, 1));
        encounterModel.tick(helper.getLevel(), player);
        helper.assertValueEqual(player.getEffect(MobEffects.HASTE).getAmplifier(), 1, "stronger external haste survives");
        player.removeAllEffects();
        encounterModel.clearStatusEffects();
        encounterModel.tick(helper.getLevel(), player);
        player.addEffect(new MobEffectInstance(MobEffects.HASTE, 200, 0));
        encounterModel.tick(helper.getLevel(), player);
        helper.assertTrue(player.getEffect(MobEffects.HASTE).getDuration() > 100, "longer external haste survives");
        player.removeAllEffects();
        PlayerDamageModel rescueModel = new PlayerDamageModel();
        AbstractDamageablePart collapsed = null;
        for (AbstractDamageablePart part : rescueModel) {
            if (part.canCauseDeath) {
                collapsed = part;
                part.currentHealth = 0.0F;
                break;
            }
        }
        helper.assertTrue(collapsed != null, "critical part must exist");
        rescueModel.handlePostDamage(player, null);
        helper.assertTrue(rescueModel.canBeRescued(), "critical injury must down the player");
        boolean wakeUpEnabled = FirstAid.rescueWakeUpEnabled;
        FirstAid.rescueWakeUpEnabled = true;
        try {
            helper.assertTrue(rescueModel.adrenalineRescueFromCriticalState(player, true), "injector rescues a downed player");
            helper.assertTrue(Math.abs(collapsed.currentHealth - 1.0F) < 0.0001F, "critical part recovers to one health");
            helper.assertTrue(rescueModel.getUnconsciousTicks() < FirstAid.getRescueWakeUpDelayTicks(), "injector shortens wake-up delay");
            helper.assertTrue(player.hasEffect(MobEffects.ABSORPTION) && !player.hasEffect(MobEffects.SPEED)
                && !player.hasEffect(MobEffects.HASTE) && !player.hasEffect(MobEffects.STRENGTH), "rescue keeps absorption without separate speed haste or strength");
            helper.assertTrue(rescueModel.getSuppressionIntensity() == 1.0F, "rescue injection immediately fills adrenaline");
        } finally {
            FirstAid.rescueWakeUpEnabled = wakeUpEnabled;
        }
        boolean fatigueEnabled = FirstAidConfig.SERVER.adrenalineFatigueEnabled.get();
        int fatigueThreshold = FirstAidConfig.SERVER.adrenalineFatigueThresholdSeconds.get();
        double fatigueRatio = FirstAidConfig.SERVER.adrenalineFatigueDurationRatio.get();
        try {
            FirstAidConfig.SERVER.adrenalineFatigueEnabled.set(true);
            FirstAidConfig.SERVER.adrenalineFatigueThresholdSeconds.set(1);
            FirstAidConfig.SERVER.adrenalineFatigueDurationRatio.set(0.10D);
            PlayerDamageModel injectorModel = new PlayerDamageModel();
            injectorModel.applyAdrenalineInjection(player);
            helper.assertTrue(injectorModel.getSuppressionIntensity() == 1.0F, "normal injection immediately fills adrenaline");
            helper.assertTrue(!player.hasEffect(RegistryObjects.PAINKILLER_EFFECT), "injection must not grant a painkiller effect");
            player.removeAllEffects();
            PlayerDamageModel fatigueModel = new PlayerDamageModel();
            fatigueModel.registerAdrenalineNearMiss(player, 0.35F, 80);
            for (int i = 0; i < 19; i++) fatigueModel.tickSuppressionState();
            helper.assertTrue(!fatigueModel.isAdrenalineFatigueReady(), "fatigue must wait for the threshold");
            fatigueModel.tickSuppressionState();
            helper.assertTrue(fatigueModel.isAdrenalineFatigueReady(), "continuous adrenaline reaches the threshold");

            player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 5.0D);
            player.setYRot(180.0F);
            helper.assertTrue(EventHandler.hasAdrenalineFatigueThreat(player, 12.0D), "hostile behind the player blocks fatigue");
            zombie.discard();
            var enderman = helper.spawn(EntityTypes.ENDERMAN, new BlockPos(2, 1, 1));
            enderman.setTarget(player);
            helper.assertTrue(EventHandler.hasAdrenalineFatigueThreat(player, 12.0D), "targeting neutral mob blocks fatigue");
            enderman.discard();
            player.setPos(100.0D, 1.0D, 100.0D);
            FakePlayer recentTarget = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.fromString("f175a1d0-e14e-4b4a-b91a-2f06fa19c2ef"), "[FirstAidTarget]"));
            recentTarget.setPos(101.0D, 1.0D, 100.0D);
            helper.getLevel().players().add(player);
            try {
                helper.assertTrue(EventHandler.hasAdrenalineFatigueThreat(recentTarget, 12.0D), "recent nearby attacker blocks fatigue");
            } finally {
                helper.getLevel().players().remove(player);
            }
            player.setPos(120.0D, 1.0D, 120.0D);
            helper.assertTrue(!EventHandler.hasAdrenalineFatigueThreat(player, 12.0D), "safe area permits fatigue");

            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 400, 0));
            fatigueModel.applyAdrenalineFatigue(player);
            helper.assertTrue(player.hasEffect(MobEffects.NAUSEA) && player.hasEffect(MobEffects.MINING_FATIGUE),
                "fatigue applies nausea and mining fatigue");
            helper.assertValueEqual(player.getEffect(MobEffects.WEAKNESS).getAmplifier(), 1, "stronger weakness survives");
            helper.assertTrue(player.getEffect(MobEffects.DARKNESS).getDuration() >= 400, "longer darkness survives");
            helper.assertTrue(!fatigueModel.isAdrenalineFatigueReady(), "one adrenaline episode triggers fatigue once");
            player.removeEffect(MobEffects.NAUSEA);
            fatigueModel.tick(helper.getLevel(), player);
            helper.assertTrue(player.hasEffect(MobEffects.NAUSEA), "fatigue survives overlap cleanup");

            CompoundTag saved = fatigueModel.serializeNBT();
            saved.putFloat("suppressionIntensity", 0.0F);
            saved.putInt("adrenalineExposureTicks", 7200);
            saved.putBoolean("adrenalineFatiguePending", true);
            saved.putBoolean("adrenalineFatigueTriggered", false);
            PlayerDamageModel pendingModel = new PlayerDamageModel();
            pendingModel.deserializeNBT(saved);
            helper.assertTrue(pendingModel.isAdrenalineFatigueReady(), "completed episode waits after adrenaline reaches zero");
            player.removeAllEffects();
            pendingModel.applyAdrenalineFatigue(player);
            helper.assertValueEqual(player.getEffect(MobEffects.DARKNESS).getDuration(), 600, "fatigue caps at 30 seconds");

            FirstAidConfig.SERVER.adrenalineFatigueDurationRatio.set(0.20D);
            saved.putInt("adrenalineExposureTicks", 1200);
            PlayerDamageModel ratioModel = new PlayerDamageModel();
            ratioModel.deserializeNBT(saved);
            player.removeAllEffects();
            ratioModel.applyAdrenalineFatigue(player);
            helper.assertValueEqual(player.getEffect(MobEffects.DARKNESS).getDuration(), 240,
                "admin ratio changes fatigue duration");

            FirstAidConfig.SERVER.adrenalineFatigueEnabled.set(false);
            PlayerDamageModel disabledModel = new PlayerDamageModel();
            saved.putBoolean("adrenalineFatiguePending", true);
            disabledModel.deserializeNBT(saved);
            helper.assertTrue(!disabledModel.isAdrenalineFatigueReady(), "admin off prevents new fatigue");
            pendingModel.tick(helper.getLevel(), player);
            helper.assertTrue(player.hasEffect(MobEffects.DARKNESS), "admin off lets existing fatigue finish");
            player.removeAllEffects();
        } finally {
            FirstAidConfig.SERVER.adrenalineFatigueEnabled.set(fatigueEnabled);
            FirstAidConfig.SERVER.adrenalineFatigueThresholdSeconds.set(fatigueThreshold);
            FirstAidConfig.SERVER.adrenalineFatigueDurationRatio.set(fatigueRatio);
        }
        helper.succeed();
        } finally {
            FirstAid.adrenalineReliefMultiplier = configuredRelief;
        }
    }

    private static void testAdrenalineThreatDecay(GameTestHelper helper, FakePlayer player) {
        PlayerDamageModel normal = new PlayerDamageModel();
        PlayerDamageModel threatened = new PlayerDamageModel();
        for (PlayerDamageModel model : List.of(normal, threatened)) {
            CompoundTag state = model.serializeNBT();
            state.putFloat("suppressionIntensity", 1.0F);
            state.putInt("suppressionHoldTicks", 0);
            model.deserializeNBT(state);
        }
        threatened.setAdrenalineThreatNearby(true);
        for (int i = 0; i < 40; i++) {
            normal.tickSuppressionState();
            threatened.tickSuppressionState();
        }
        helper.assertTrue(Math.abs(normal.getSuppressionIntensity() - 0.85F) < 0.0001F, "ordinary pressure decay remains unchanged");
        helper.assertTrue(Math.abs(threatened.getSuppressionIntensity() - 0.94F) < 0.0001F, "nearby danger cuts decay by sixty percent");
        threatened.setAdrenalineThreatNearby(false);
        for (int i = 0; i < 4; i++) threatened.tickSuppressionState();
        helper.assertTrue(Math.abs(threatened.getSuppressionIntensity() - 0.925F) < 0.0001F, "normal decay resumes when danger leaves");
        threatened.setAdrenalineThreatNearby(true);
        threatened.clearStatusEffects();
        CompoundTag state = threatened.serializeNBT();
        state.putFloat("suppressionIntensity", 1.0F);
        state.putInt("suppressionHoldTicks", 4);
        threatened.deserializeNBT(state);
        for (int i = 0; i < 4; i++) threatened.tickSuppressionState();
        helper.assertTrue(threatened.getSuppressionIntensity() == 1.0F, "the hold period still prevents decay");
        for (int i = 0; i < 4; i++) threatened.tickSuppressionState();
        helper.assertTrue(Math.abs(threatened.getSuppressionIntensity() - 0.985F) < 0.0001F, "clearing medical state removes the cached danger flag");

        PlayerDamageModel model = (PlayerDamageModel) CommonUtils.getDamageModel(player);
        player.removeAllEffects();
        model.clearStatusEffects();
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
        model.ensureEncounterAdrenaline(player, 0.225F);
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        PlayerDamageModel before = new PlayerDamageModel();
        before.deserializeNBT(model.serializeNBT());
        model.LEFT_ARM.currentHealth -= 2.0F;
        model.registerAdrenalineDamage(player, before);
        model.tick(helper.getLevel(), player);
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.205F) < 0.0001F, "a one-heart hit no longer drops 22.5 percent pressure below the first boost tier");
        for (var effect : List.of(RegistryObjects.ADRENALINE_SPEED_EFFECT,
                RegistryObjects.ADRENALINE_ATTACK_SPEED_EFFECT, RegistryObjects.ADRENALINE_STRENGTH_EFFECT)) {
            MobEffectInstance boost = player.getEffect(effect);
            helper.assertTrue(boost != null && boost.showIcon() && boost.getAmplifier() == 0,
                "hunger and weakness coexist with all three pressure icons after an injury");
        }
        helper.assertTrue(player.hasEffect(MobEffects.HUNGER) && player.hasEffect(MobEffects.WEAKNESS), "unrelated negative effects remain intact");
        player.removeAllEffects();
        model.clearStatusEffects();
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
    }

    private static void testCombatAdrenalineLimits(GameTestHelper helper) {
        var adrenalineCommand = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("firstaid").getChild("adrenaline");
        for (String literal : new String[]{"maxlevel", "attackgain", "attackcooldown", "attackhold"}) {
            helper.assertTrue(adrenalineCommand.getChild(literal) != null, "adrenaline command must expose " + literal);
        }
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        float savedGain = FirstAid.suppressionGainMultiplier;
        int savedMaxLevel = FirstAidConfig.SERVER.adrenalineMaxLevel.get();
        double savedAttackGain = FirstAidConfig.SERVER.adrenalineAttackGainMultiplier.get();
        int savedCooldown = FirstAidConfig.SERVER.adrenalineAttackCooldownTicks.get();
        try {
            FirstAid.suppressionGainMultiplier = 0.15F;
            FirstAidConfig.SERVER.adrenalineMaxLevel.set(5);
            FirstAidConfig.SERVER.adrenalineAttackGainMultiplier.set(1.0D);
            FirstAidConfig.SERVER.adrenalineAttackCooldownTicks.set(0);
            PlayerDamageModel sweep = new PlayerDamageModel();
            for (int i = 0; i < 5; ++i) sweep.registerAdrenalineAttack(player, 0.50F, 40);
            helper.assertTrue(Math.abs(sweep.getSuppressionIntensity() - 0.06F) < 0.0001F, "hits landing in one tick count as a single attack");
            helper.assertValueEqual(sweep.getSuppressionHoldTicks(), 40, "a counted hit applies the hold time");

            FirstAidConfig.SERVER.adrenalineAttackGainMultiplier.set(0.5D);
            PlayerDamageModel halved = new PlayerDamageModel();
            halved.registerAdrenalineAttack(player, 0.50F, 40);
            helper.assertTrue(Math.abs(halved.getSuppressionIntensity() - 0.03F) < 0.0001F, "attack gain multiplier scales hit growth");

            FirstAidConfig.SERVER.adrenalineAttackGainMultiplier.set(0.0D);
            PlayerDamageModel disabled = new PlayerDamageModel();
            disabled.registerAdrenalineAttack(player, 0.50F, 40);
            helper.assertTrue(disabled.getSuppressionIntensity() == 0.0F, "zero attack gain disables hit growth");
            helper.assertValueEqual(disabled.getSuppressionHoldTicks(), 0, "disabled hit growth must not freeze decay");
            disabled.registerAdrenalineNearMiss(player, 0.35F, 0);
            helper.assertTrue(disabled.getSuppressionIntensity() > 0.0F, "zero attack gain leaves near misses alone");
            FirstAidConfig.SERVER.adrenalineAttackGainMultiplier.set(1.0D);

            int[] expectedLevels = {0, 1, 2, 3, 4, 5};
            for (int maxLevel = 0; maxLevel <= 5; ++maxLevel) {
                FirstAidConfig.SERVER.adrenalineMaxLevel.set(maxLevel);
                PlayerDamageModel capped = new PlayerDamageModel();
                for (int i = 0; i < 400; ++i) capped.registerAdrenalineNearMiss(player, 1.45F, 0);
                helper.assertValueEqual(capped.getAdrenalineLevel(), expectedLevels[maxLevel], "near misses stop at the configured tier " + maxLevel);
                PlayerDamageModel encounter = new PlayerDamageModel();
                encounter.ensureEncounterAdrenaline(player, 1.0F);
                helper.assertValueEqual(encounter.getAdrenalineLevel(), expectedLevels[maxLevel], "encounters stop at the configured tier " + maxLevel);
                for (int i = 0; i < 600; ++i) encounter.recoverEncounterAdrenaline(player, 20);
                helper.assertTrue(encounter.getAdrenalineLevel() <= maxLevel, "encounter recovery respects the configured tier " + maxLevel);
            }

            FirstAidConfig.SERVER.adrenalineMaxLevel.set(2);
            PlayerDamageModel injected = new PlayerDamageModel();
            CompoundTag state = injected.serializeNBT();
            state.putFloat("suppressionIntensity", 1.0F);
            injected.deserializeNBT(state);
            injected.registerAdrenalineNearMiss(player, 0.35F, 80);
            helper.assertTrue(injected.getSuppressionIntensity() == 1.0F, "the cap never lowers adrenaline from stronger sources");
            helper.assertValueEqual(injected.getAdrenalineLevel(), 5, "stronger sources keep their tier");
            helper.assertValueEqual(injected.getSuppressionHoldTicks(), 0, "combat above the cap does not extend the hold of a stronger source");
        } finally {
            FirstAid.suppressionGainMultiplier = savedGain;
            FirstAidConfig.SERVER.adrenalineMaxLevel.set(savedMaxLevel);
            FirstAidConfig.SERVER.adrenalineAttackGainMultiplier.set(savedAttackGain);
            FirstAidConfig.SERVER.adrenalineAttackCooldownTicks.set(savedCooldown);
            player.removeAllEffects();
        }
    }

    private static void testAdrenalineReliefMultiplier(GameTestHelper helper) {
        float saved = FirstAid.adrenalineReliefMultiplier;
        try {
            var legs = List.of(new ConstantDebuffEntry(0.33F, 3), new ConstantDebuffEntry(0.60F, 2), new ConstantDebuffEntry(0.80F, 1));
            var custom = List.of(new ConstantDebuffEntry(0.30F, 1));
            FirstAid.adrenalineReliefMultiplier = 1.5F;
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(legs, 0.36F, 1.0F), 0, "150 percent protection shifts the first leg threshold to 35 percent");
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(legs, 0.35F, 1.0F), 1, "adjusted threshold remains inclusive");
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(legs, 0.01F, 1.0F), 1, "severe combat injury still has a level-one cap");
            var arms = List.of(new ConstantDebuffEntry(0.40F, 2), new ConstantDebuffEntry(0.75F, 1));
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(arms, 0.38F, 1.0F), 0, "150 percent arm protection starts below 38 percent");
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(arms, 0.375F, 1.0F), 1, "adjusted arm threshold remains inclusive");
            FirstAid.adrenalineReliefMultiplier = 1.7F;
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(legs, 0.30F, 1.0F), 0, "170 percent relief protects legs above the new 29 percent threshold");
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(legs, 0.2899F, 1.0F), 1, "170 percent relief still permits severe leg debuffs");
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(arms, 0.33F, 1.0F), 0, "170 percent relief protects arms above 32.5 percent");
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(arms, 0.325F, 1.0F), 1, "170 percent relief retains the level-one arm cap");
            FirstAid.adrenalineReliefMultiplier = 0.0F;
            helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(legs, 0.40F, 1.0F), 2, "zero multiplier restores original severe injury levels");
            PlayerDamageModel model = new PlayerDamageModel();
            CompoundTag state = model.serializeNBT();
            state.putFloat("chronicPainIntensity", 1.0F);
            state.putFloat("acutePainIntensity", 1.0F);
            state.putFloat("suppressionIntensity", 0.80F);
            model.deserializeNBT(state);
            for (float strength : new float[]{0.0F, 0.5F, 1.0F, 1.5F, 1.7F, 2.0F}) {
                FirstAid.adrenalineReliefMultiplier = strength;
                helper.assertTrue(Math.abs(model.getPainVisualStrength(false) - 0.95F) < 0.0001F, "pain blur remains unchanged at every configurable protection strength");
                helper.assertTrue(Math.abs(model.getPainFovStrength(true) - 0.25F) < 0.0001F, "medicine breakthrough behavior stays unchanged");
                helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(custom, 0.30F, 1.0F), 0, "strict custom thresholds stay strict");
                for (int i = 0; i <= 1000; i++) {
                    float health = i / 1000.0F;
                    int original = health < 0.33F ? 3 : health < 0.60F ? 2 : health < 0.80F ? 1 : 0;
                    helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(legs, health, 0.0F), original, "zero-pressure behavior is independent of relief configuration");
                }
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
                var codec = ichttt.mods.firstaid.common.network.MessageSyncCommandSettings.STREAM_CODEC;
                try {
                    codec.encode(buffer, ichttt.mods.firstaid.common.network.MessageSyncCommandSettings.current());
                    var decoded = codec.decode(buffer);
                    buffer.clear();
                    codec.encode(buffer, decoded);
                    for (int i = 0; i < 6; i++) buffer.readBoolean();
                    helper.assertTrue(Math.abs(buffer.readFloat() - strength) < 0.0001F, "configured relief must survive server-to-client codec round trip");
                } finally { buffer.release(); }
            }
            FirstAid.adrenalineReliefMultiplier = 1.0F;
            helper.assertTrue(Math.abs(model.getPainFovStrength(false) - 0.95F) < 0.0001F, "one restores previous FOV protection");
            FirstAid.adrenalineReliefMultiplier = 1.5F;
            helper.assertTrue(Math.abs(model.getPainFovStrength(false) - 0.475F) < 0.0001F, "150 percent FOV protection is independent of blur");
            FirstAid.adrenalineReliefMultiplier = 1.7F;
            helper.assertTrue(Math.abs(model.getPainFovStrength(false) - 0.405F) < 0.0001F, "170 percent relief lowers pain FOV without changing blur");
            FirstAid.adrenalineReliefMultiplier = 0.0F;
            helper.assertTrue(Math.abs(model.getPainFovStrength(false) - PlayerDamageModel.PAIN_HARD_CAP) < 0.0001F, "zero turns off pressure protection for FOV");
            var staleSettings = ichttt.mods.firstaid.common.network.MessageSyncCommandSettings.current();
            FirstAid.adrenalineReliefMultiplier = 1.5F;
            var context = (net.neoforged.neoforge.network.handling.IPayloadContext) java.lang.reflect.Proxy.newProxyInstance(
                net.neoforged.neoforge.network.handling.IPayloadContext.class.getClassLoader(),
                new Class<?>[]{net.neoforged.neoforge.network.handling.IPayloadContext.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("enqueueWork")) {
                        ((Runnable) arguments[0]).run();
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
            ichttt.mods.firstaid.common.network.MessageSyncCommandSettings.handle(staleSettings, context);
            helper.assertTrue(FirstAid.adrenalineReliefMultiplier == 1.5F, "an old local-client reply must not undo a newer server command");
            var branch = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("firstaid");
            helper.assertTrue(branch.getChild("adrenaline").getChild("relief") != null && branch.getChild("suppression").getChild("relief") != null, "both command aliases expose configurable relief");
        } finally { FirstAid.adrenalineReliefMultiplier = saved; }
    }

    private static void testInjuryDebuffRecovery(GameTestHelper helper, FakePlayer player) {
        PlayerDamageModel model = (PlayerDamageModel) CommonUtils.getDamageModel(player);
        player.removeAllEffects();
        model.clearStatusEffects();
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
        for (var part : new AbstractDamageablePart[]{model.LEFT_LEG, model.RIGHT_LEG, model.LEFT_FOOT,
                model.RIGHT_FOOT, model.LEFT_ARM, model.RIGHT_ARM, model.BODY}) {
            part.currentHealth = part.getMaxHealth() * 0.40F;
        }
        model.BODY.currentHealth = model.BODY.getMaxHealth() * 0.29F;
        model.onLookupsReloaded();
        helper.assertTrue(model.getSuppressionIntensity() == 0.0F && !model.isPainSuppressed(player), "recovery test has no pressure or painkillers");
        model.tick(helper.getLevel(), player);
        helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "lookup reload must restore slowness from current injury without another hit");
        helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 1, "reloaded leg injury retains original level two");
        helper.assertTrue(player.hasEffect(MobEffects.MINING_FATIGUE), "lookup reload must restore shared arm debuffs");
        helper.assertValueEqual(player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier(), 1, "reloaded arm injury retains original level two");
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "lookup reload must restore individual body debuffs");

        for (var medicine : List.of(RegistryObjects.PAINKILLER_EFFECT, RegistryObjects.MORPHINE_EFFECT)) {
            model.clearStatusEffects();
            player.removeAllEffects();
            for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
            model.onLookupsReloaded();
            player.addEffect(new MobEffectInstance(medicine, 40, 0));
            for (var part : new AbstractDamageablePart[]{model.LEFT_LEG, model.RIGHT_LEG, model.LEFT_FOOT, model.RIGHT_FOOT}) {
                part.damage(part.getMaxHealth() * 0.60F, player, true);
            }
            model.tick(helper.getLevel(), player);
            helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "medicine still suppresses injury debuffs");
            player.removeEffect(medicine);
            model.tick(helper.getLevel(), player);
            helper.assertTrue(!model.isPainSuppressed(player), "removed medicine leaves no full pain suppression");
            helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "injury acquired during medicine must return without another hit");
            helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 1, "medicine ending restores original leg injury level");
        }

        model.clearStatusEffects();
        player.removeAllEffects();
        FirstAid.InjuryDebuffMode configuredMode = FirstAid.injuryDebuffMode;
        try {
            ConstantDebuff toggled = new ConstantDebuff(Identifier.parse("minecraft:slowness"),
                List.of(new ConstantDebuffEntry(0.60F, 2), new ConstantDebuffEntry(0.80F, 1)));
            FirstAid.injuryDebuffMode = FirstAid.InjuryDebuffMode.OFF;
            toggled.update(player, 0.40F);
            helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "disabled injury effects stay disabled");
            FirstAid.injuryDebuffMode = FirstAid.InjuryDebuffMode.NORMAL;
            toggled.update(player, 0.40F);
            helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "re-enabled injury effects recover from current health");
            helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 1, "re-enabled injury retains original level");
        } finally {
            FirstAid.injuryDebuffMode = configuredMode;
        }

        model.clearStatusEffects();
        player.removeAllEffects();
        model.applyTrackedInjuryEffect(player, "minecraft:slowness", new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        MobEffectInstance external = new MobEffectInstance(MobEffects.SLOWNESS, 400, 3);
        player.addEffect(external);
        model.onEffectAdded(player, external);
        model.applyTrackedInjuryEffect(player, "minecraft:slowness", new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 3, "active external slowness stays protected");
        CompoundTag savedOverlap = model.serializeNBT();
        player.removeEffect(MobEffects.SLOWNESS);
        model.deserializeNBT(savedOverlap);
        model.applyTrackedInjuryEffect(player, "minecraft:slowness", new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "expired external overlap cannot permanently block injury effects");
        helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 1, "original injury debuff returns after overlap removal");
        model.clearStatusEffects();
        player.removeAllEffects();
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
        model.onLookupsReloaded();
    }

    private static void testEncounterCrowdRecovery(GameTestHelper helper, FakePlayer player) {
        double configuredBase = FirstAidConfig.SERVER.encounterBaseIntensity.get();
        FirstAidConfig.SERVER.encounterBaseIntensity.set(0.20D);
        try {
            int[] threats = {0, 1, 2, 3, 4, 5, 6, 20};
            float[] firstGains = {0.0F, 0.01F, 0.014F, 0.018F, 0.022F, 0.026F, 0.03F, 0.03F};
            float[] ceilings = {0.0F, 0.20F, 0.26F, 0.32F, 0.38F, 0.44F, 0.50F, 0.50F};
            for (int i = 0; i < threats.length; ++i) {
                PlayerDamageModel model = new PlayerDamageModel();
                model.recoverEncounterAdrenaline(player, threats[i]);
                helper.assertTrue(Math.abs(model.getSuppressionIntensity() - firstGains[i]) < 0.0001F, "crowd size scales the slow first recovery step");
                for (int j = 0; j < 300; ++j) model.recoverEncounterAdrenaline(player, threats[i]);
                helper.assertTrue(Math.abs(model.getSuppressionIntensity() - ceilings[i]) < 0.0001F, "crowd recovery reaches its bounded ceiling");
                if (threats[i] > 0) {
                    model.tickSuppressionState();
                    model.recoverEncounterAdrenaline(player, threats[i]);
                    helper.assertValueEqual(model.getSuppressionHoldTicks(), 40, "holding the exact ceiling prevents threshold flicker");
                }
            }
            PlayerDamageModel high = new PlayerDamageModel();
            CompoundTag tag = high.serializeNBT();
            tag.putFloat("suppressionIntensity", 0.80F);
            tag.putInt("suppressionHoldTicks", 7);
            high.deserializeNBT(tag);
            high.recoverEncounterAdrenaline(player, 20);
            helper.assertTrue(high.getSuppressionIntensity() == 0.80F && high.getSuppressionHoldTicks() == 7, "crowd recovery does not pin or reduce active high pressure above its ceiling");
            tag.putFloat("suppressionIntensity", 0.20F);
            high.deserializeNBT(tag);
            high.recoverEncounterAdrenaline(player, 2);
            helper.assertTrue(Math.abs(high.getSuppressionIntensity() - 0.2112F) < 0.0001F, "passive recovery also diminishes with existing pressure");
            PlayerDamageModel before = new PlayerDamageModel();
            before.deserializeNBT(high.serializeNBT());
            high.LEFT_ARM.currentHealth -= 1.0F;
            high.registerAdrenalineDamage(player, before);
            float injuredPressure = high.getSuppressionIntensity();
            high.recoverEncounterAdrenaline(player, 6);
            helper.assertTrue(high.getSuppressionIntensity() == injuredPressure, "crowd recovery respects the post-injury delay");
            for (int j = 0; j < 40; ++j) high.tick(helper.getLevel(), player);
            injuredPressure = high.getSuppressionIntensity();
            high.recoverEncounterAdrenaline(player, 6);
            helper.assertTrue(high.getSuppressionIntensity() > injuredPressure, "crowd recovery resumes after forty ticks");
            FirstAidConfig.SERVER.encounterBaseIntensity.set(0.72D);
            PlayerDamageModel custom = new PlayerDamageModel();
            for (int j = 0; j < 300; ++j) custom.recoverEncounterAdrenaline(player, 20);
            helper.assertTrue(Math.abs(custom.getSuppressionIntensity() - 0.72F) < 0.0001F, "a configured base above the default cap is preserved");
        } finally {
            FirstAidConfig.SERVER.encounterBaseIntensity.set(configuredBase);
            player.removeAllEffects();
        }
    }

    private static void testAdrenalineMechanics(GameTestHelper helper, FakePlayer player) {
        PlayerDamageModel model = (PlayerDamageModel) CommonUtils.getDamageModel(player);
        model.clearStatusEffects();
        player.removeAllEffects();
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
        var legs = List.of(new ConstantDebuffEntry(0.33F, 3), new ConstantDebuffEntry(0.60F, 2), new ConstantDebuffEntry(0.80F, 1));
        var arms = List.of(new ConstantDebuffEntry(0.25F, 3), new ConstantDebuffEntry(0.50F, 2), new ConstantDebuffEntry(0.75F, 1));
        var body = List.of(new ConstantDebuffEntry(0.25F, 2), new ConstantDebuffEntry(0.50F, 1));
        for (var boundaries : List.of(legs, arms, body)) {
            for (int i = 0; i <= 1000; ++i) {
                float health = i / 1000.0F;
                int original = 0;
                for (var entry : boundaries) {
                    if (health < entry.healthFractionThreshold()) { original = entry.effectAmplifier(); break; }
                }
                helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(boundaries, health, 0.0F), original,
                    "zero-pressure boundaries must equal the original algorithm");
            }
            for (float pressure : new float[]{0.35F, 0.70F, 1.0F}) {
                PlayerDamageModel state = new PlayerDamageModel();
                state.ensureEncounterAdrenaline(player, pressure);
                float relief = state.getAdrenalineDebuffRelief();
                helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(boundaries, 0.51F, relief), 0, "51 percent has no combat injury debuff");
                helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(boundaries, 0.50F, relief), 1, "50 percent starts level one");
                helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(boundaries, 0.01F, relief), 1, "critical injury is capped at level one during combat");
                state.clearStatusEffects();
                state.tick(helper.getLevel(), player);
            }
        }
        var custom = List.of(new ConstantDebuffEntry(0.30F, 1));
        helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(custom, 0.31F, 1.0F), 0, "custom lower thresholds cannot become harsher");
        helper.assertValueEqual(ConstantDebuff.calculateAdrenalineMultiplier(custom, 0.30F, 1.0F), 0, "custom strict boundary remains strict");
        helper.assertTrue(ConstantDebuff.calculateAdrenalineMultiplier(legs, 0.65F, 0.5F) <= 1, "transition must lower debuff strength");

        ConstantDebuff debuff = new ConstantDebuff(Identifier.parse("minecraft:slowness"), legs);
        debuff.handleHealing(0.0F, 0.20F, player);
        debuff.update(player, 0.20F);
        helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 2, "original severe slowness remains outside combat");
        model.ensureEncounterAdrenaline(player, 0.35F);
        debuff.update(player, 0.51F);
        helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "combat transition clears only owned obsolete slowness");
        debuff.update(player, 0.50F);
        helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 0, "owned severe debuff becomes level one immediately");
        CompoundTag ended = model.serializeNBT();
        ended.putFloat("suppressionIntensity", 0.0F);
        model.deserializeNBT(ended);
        debuff.update(player, 0.20F);
        helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 2, "depression of pressure restores the original severity");
        MobEffectInstance external = new MobEffectInstance(MobEffects.SLOWNESS, 400, 3);
        player.addEffect(external);
        model.onEffectAdded(player, external);
        model.ensureEncounterAdrenaline(player, 0.35F);
        debuff.update(player, 0.51F);
        helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 3, "external overlapping slowness survives combat adjustment");

        player.removeAllEffects();
        model.clearStatusEffects();
        model.ensureEncounterAdrenaline(player, 0.35F);
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
        model.LEFT_LEG.currentHealth = 0.0F;
        SharedDebuff shared = new SharedDebuff(new ConstantDebuff(Identifier.parse("minecraft:slowness"), legs), EnumDebuffSlot.LEGS_AND_FEET);
        shared.handleDamageTaken(1.0F, 0.0F, player);
        shared.tick(player);
        helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "one lost leg does not bypass the four-part aggregate threshold");
        model.RIGHT_LEG.currentHealth = 0.0F;
        shared.handleDamageTaken(1.0F, 0.0F, player);
        shared.tick(player);
        helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 0, "two lost parts put aggregate health at half");

        model.clearStatusEffects();
        player.removeAllEffects();
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
        model.onLookupsReloaded();
        model.tick(helper.getLevel(), player);
        player.removeAllEffects();
        double baseSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double baseAttackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double baseAttackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float[] thresholds = {0.20F, 0.35F, 0.50F, 0.65F, 0.80F};
        for (int i = 0; i < thresholds.length; ++i) {
            CompoundTag state = model.serializeNBT();
            state.putFloat("suppressionIntensity", thresholds[i] - 0.0001F);
            state.putInt("suppressionHoldTicks", 300);
            state.putInt("adrenalineBurstTicks", 80);
            state.putInt("adrenalineBurstCooldownTicks", 400);
            model.deserializeNBT(state);
            model.tick(helper.getLevel(), player);
            helper.assertValueEqual(model.getAdrenalineLevel(), i, "just below a tier keeps the previous level");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttackDamage - i * 0.6D) < 0.0001D, "strength keeps its previous tier below the boundary");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) / baseSpeed - (1.0D + i * 0.04D)) < 0.0001D, "movement scales with each pressure tier");
            model.ensureEncounterAdrenaline(player, thresholds[i]);
            helper.assertValueEqual(model.getAdrenalineLevel(), i + 1, "pressure boundary enters the next level");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttackDamage - (i + 1) * 0.6D) < 0.0001D, "strength adds six tenths of damage per tier");
            for (var effect : java.util.List.of(RegistryObjects.ADRENALINE_SPEED_EFFECT, RegistryObjects.ADRENALINE_ATTACK_SPEED_EFFECT, RegistryObjects.ADRENALINE_STRENGTH_EFFECT)) {
                MobEffectInstance boost = player.getEffect(effect);
                helper.assertTrue(boost != null && boost.showIcon() && !boost.isVisible() && boost.isInfiniteDuration(), "pressure gains show icons without particles");
                helper.assertValueEqual(boost.getAmplifier(), i, "visible gain matches the actual pressure tier");
            }
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) / baseSpeed - (1.0D + (i + 1) * 0.04D)) < 0.0001D, "tier upgrade updates the existing movement modifier");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) / baseAttackSpeed - (1.0D + (i + 1) * 0.04D)) < 0.0001D, "attack speed scales with each pressure tier");
        }
        model.ensureEncounterAdrenaline(player, 1.0F);
        for (int i = 0; i < 100; ++i) model.tick(helper.getLevel(), player);
        helper.assertValueEqual(model.getAdrenalineLevel(), 5, "full pressure reaches level five");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) / baseSpeed - 1.20D) < 0.0001D, "natural movement continues beyond four seconds");
        PlayerDamageModel restored = new PlayerDamageModel();
        restored.deserializeNBT(model.serializeNBT());
        helper.assertValueEqual(restored.getAdrenalineLevel(), 5, "pressure tiers survive save load");
        helper.assertValueEqual(restored.serializeNBT().getIntOr("adrenalineBurstCooldownTicks", -1), 0, "legacy burst cooldown cannot block sustained gains");
        model.LEFT_FOOT.currentHealth = model.LEFT_FOOT.getMaxHealth() * 0.25F;
        model.tick(helper.getLevel(), player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 0.0001D, "new leg injury removes movement bonus");
        model.LEFT_FOOT.currentHealth = model.LEFT_FOOT.getMaxHealth();
        model.RIGHT_ARM.currentHealth = model.RIGHT_ARM.getMaxHealth() * 0.25F;
        model.tick(helper.getLevel(), player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - baseAttackSpeed) < 0.0001D, "severe arm injury removes attack bonus");
        helper.assertTrue(!player.hasEffect(RegistryObjects.ADRENALINE_STRENGTH_EFFECT) && Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttackDamage) < 0.0001D, "severe arm injury also removes strength");
        model.RIGHT_ARM.currentHealth = model.RIGHT_ARM.getMaxHealth();
        model.BODY.currentHealth = model.BODY.getMaxHealth() * 0.25F;
        model.tick(helper.getLevel(), player);
        helper.assertTrue(!player.hasEffect(RegistryObjects.ADRENALINE_STRENGTH_EFFECT), "critical torso injury removes strength");
        player.removeAllEffects();
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 0.0001D
            && Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - baseAttackSpeed) < 0.0001D, "critical torso injury disables combat benefits");
        model.BODY.currentHealth = model.BODY.getMaxHealth();
        model.onLookupsReloaded();
        ended = model.serializeNBT();
        ended.putFloat("suppressionIntensity", 0.19F);
        model.deserializeNBT(ended);
        model.tick(helper.getLevel(), player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 0.0001D
            && Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - baseAttackSpeed) < 0.0001D, "falling below twenty percent removes positive modifiers");
        helper.assertTrue(!player.hasEffect(RegistryObjects.ADRENALINE_SPEED_EFFECT)
            && !player.hasEffect(RegistryObjects.ADRENALINE_ATTACK_SPEED_EFFECT)
            && !player.hasEffect(RegistryObjects.ADRENALINE_STRENGTH_EFFECT), "falling below twenty percent removes all positive icons immediately");
        for (float pressure : new float[]{0.80F, 0.35F, 0.65F, 0.20F}) {
            ended = model.serializeNBT();
            ended.putFloat("suppressionIntensity", pressure);
            ended.putInt("suppressionHoldTicks", 300);
            model.deserializeNBT(ended);
            model.tick(helper.getLevel(), player);
            helper.assertValueEqual(player.getEffect(RegistryObjects.ADRENALINE_SPEED_EFFECT).getAmplifier(), model.getAdrenalineLevel() - 1, "tier changes replace icons without a hidden old tier");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) / baseSpeed - (1.0D + model.getAdrenalineLevel() * 0.04D)) < 0.0001D, "downward and upward tier changes preserve calibrated movement");
        }


        model.clearStatusEffects();
        player.removeAllEffects();
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
        model.ensureEncounterAdrenaline(player, 0.50F);
        PlayerDamageModel before = new PlayerDamageModel();
        before.deserializeNBT(model.serializeNBT());
        model.LEFT_ARM.currentHealth -= 2.0F;
        model.registerAdrenalineDamage(player, before);
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.48F) < 0.0001F, "one heart of actual injury lowers pressure two points");
        before.deserializeNBT(model.serializeNBT());
        model.registerAdrenalineDamage(player, before);
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.48F) < 0.0001F, "no injury means no pressure loss");
        model.ensureEncounterAdrenaline(player, 0.80F);
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.48F) < 0.0001F, "encounter floor cannot undo a fresh hit");
        restored.deserializeNBT(model.serializeNBT());
        helper.assertValueEqual(restored.serializeNBT().getIntOr("encounterRecoveryTicks", 0), 40, "encounter recovery delay survives save load");
        for (int i = 0; i < 40; ++i) model.tick(helper.getLevel(), player);
        model.ensureEncounterAdrenaline(player, 0.80F);
        helper.assertTrue(model.getSuppressionIntensity() >= 0.80F, "encounter floor resumes after forty ticks");

        model.clearStatusEffects();
        for (AbstractDamageablePart part : model) {
            part.currentHealth = part.getMaxHealth();
            part.setAbsorption(10.0F);
        }
        model.ensureEncounterAdrenaline(player, 0.80F);
        DamageDistribution.handleDamageTaken(RandomDamageDistributionAlgorithm.NEAREST_NOKILL, model, 1.0F, player,
            player.damageSources().generic(), false, true);
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.80F) < 0.0001F, "fully absorbed damage cannot lower pressure");
        helper.assertValueEqual(model.serializeNBT().getIntOr("encounterRecoveryTicks", 0), 0, "absorbed damage cannot delay encounter recovery");
        for (AbstractDamageablePart part : model) part.setAbsorption(0.0F);
        Consumer<FirstAidLivingDamageEvent> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(FirstAidLivingDamageEvent.class, cancel);
        try {
            DamageDistribution.handleDamageTaken(RandomDamageDistributionAlgorithm.NEAREST_NOKILL, model, 2.0F, player,
                player.damageSources().generic(), false, true);
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.80F) < 0.0001F, "canceled damage cannot lower pressure");
        helper.assertValueEqual(model.serializeNBT().getIntOr("encounterRecoveryTicks", 0), 0, "cancellation cannot start recovery delay");
        before.deserializeNBT(model.serializeNBT());
        for (AbstractDamageablePart part : model) if (!part.canCauseDeath) part.currentHealth = part.getMaxHealth() * 0.50F;
        model.registerAdrenalineDamage(player, before);
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.70F) < 0.0001F, "single injury loss is capped at ten pressure points");

        model.clearStatusEffects();
        player.removeAllEffects();
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
        model.onLookupsReloaded();
        model.tick(helper.getLevel(), player);
        player.removeAllEffects();
        model.ensureEncounterAdrenaline(player, 1.0F);
        double naturalSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double naturalAttackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double naturalDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        model.applyAdrenalineInjection(player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - naturalSpeed) < 0.0001D, "injection matches natural full movement without stacking");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - naturalAttackSpeed) < 0.0001D, "injection matches natural full attack speed without stacking");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - naturalDamage) < 0.0001D, "injection and natural full pressure provide the same strength");
        helper.assertTrue(!model.isPainSuppressed(player), "injection provides partial pressure relief only");
        helper.assertTrue(player.hasEffect(MobEffects.ABSORPTION) && player.hasEffect(MobEffects.REGENERATION)
            && player.hasEffect(RegistryObjects.ADRENALINE_SPEED_EFFECT) && !player.hasEffect(MobEffects.SPEED), "injection keeps absorption regeneration and pressure-based speed");
        helper.assertTrue(!player.hasEffect(MobEffects.HASTE) && !player.hasEffect(MobEffects.STRENGTH), "injection never grants haste or strength");
        helper.assertValueEqual(model.serializeNBT().getIntOr("adrenalineBurstTicks", 0), 0, "injection cannot stack a natural burst");
        before.deserializeNBT(model.serializeNBT());
        model.RIGHT_ARM.currentHealth -= 1.0F;
        model.registerAdrenalineDamage(player, before);
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.99F) < 0.0001F, "injection hold does not protect pressure from injury");
        player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, 900, 0));
        model.applyAdrenalineInjection(player);
        helper.assertValueEqual(player.getEffect(RegistryObjects.PAINKILLER_EFFECT).getDuration(), 900, "injection neither clears nor extends existing painkillers");
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.HASTE, 120, 2));
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 90, 1));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 900, 2));
        model.applyAdrenalineInjection(player);
        helper.assertValueEqual(player.getEffect(MobEffects.HASTE).getDuration(), 120, "injection does not extend external haste");
        helper.assertValueEqual(player.getEffect(MobEffects.HASTE).getAmplifier(), 2, "external haste remains unchanged");
        helper.assertValueEqual(player.getEffect(MobEffects.STRENGTH).getDuration(), 90, "injection does not extend external strength");
        helper.assertValueEqual(player.getEffect(MobEffects.STRENGTH).getAmplifier(), 1, "external strength remains unchanged");
        helper.assertTrue(!player.hasEffect(RegistryObjects.ADRENALINE_STRENGTH_EFFECT), "external Strength suppresses pressure Strength without stacking");
        helper.assertValueEqual(player.getEffect(MobEffects.SPEED).getDuration(), 900, "stronger longer external speed survives injection");
        helper.assertValueEqual(player.getEffect(MobEffects.SPEED).getAmplifier(), 2, "injection preserves stronger external speed");
        helper.assertTrue(!player.hasEffect(RegistryObjects.ADRENALINE_SPEED_EFFECT), "external speed suppresses the pressure speed effect without stacking");
        player.removeAllEffects();
        model.tick(helper.getLevel(), player);
        helper.assertTrue(player.hasEffect(RegistryObjects.ADRENALINE_SPEED_EFFECT), "pressure speed resumes after external speed ends");
        helper.assertTrue(player.hasEffect(RegistryObjects.ADRENALINE_STRENGTH_EFFECT), "pressure strength resumes after external Strength ends");
        player.removeAllEffects();
        new ichttt.mods.firstaid.api.medicine.MedicineUseContext(player, helper.getLevel(), net.minecraft.world.item.ItemStack.EMPTY, null).applyAdrenalineInjection();
        helper.assertTrue(player.hasEffect(MobEffects.ABSORPTION) && player.hasEffect(MobEffects.REGENERATION)
            && !player.hasEffect(MobEffects.SPEED) && !player.hasEffect(MobEffects.HASTE)
            && !player.hasEffect(MobEffects.STRENGTH) && !player.hasEffect(RegistryObjects.PAINKILLER_EFFECT), "medicine fallback also avoids separate combat boosts and painkillers");
        player.removeAllEffects();
        CompoundTag pain = model.serializeNBT();
        pain.putFloat("chronicPainIntensity", 1.0F);
        pain.putFloat("acutePainIntensity", 1.0F);
        pain.putFloat("suppressionIntensity", 0.80F);
        model.deserializeNBT(pain);
        helper.assertTrue(Math.abs(model.getPainVisualStrength(false) - 0.95F) < 0.0001F, "high pressure leaves thirty percent chronic and sixty-five percent acute pain");
        helper.assertTrue(Math.abs(model.getPainVisualStrength(true) - 0.25F) < 0.0001F, "medicine breakthrough behavior remains unchanged");
        pain.putInt("painLevel", 5);
        pain.putFloat("acutePainIntensity", 0.0F);
        model.deserializeNBT(pain);
        helper.assertValueEqual(PlayerDamageModel.getPerceivedPainLevel(model), 2, "HUD uses the same partial chronic pain formula");
        helper.assertValueEqual(model.getPainLevel(), 5, "raw pain remains unchanged by stress relief");
        model.clearStatusEffects();
        for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
        model.tick(helper.getLevel(), player);
        player.removeAllEffects();
    }
}
