package ichttt.mods.firstaid.common.damagesystem;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;


public final class SuppressionRegressionGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(FirstAid.MODID, "suppression_regression");

    private SuppressionRegressionGameTests() {}

    public static void register(IEventBus bus) {
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
        helper.assertTrue(Math.abs(model.getSuppressionIntensity() - 0.0546F) < 0.0001F, "near miss gain must use 0.15 coefficient");
        FirstAid.suppressionGainMultiplier = 0.24F;
        PlayerDamageModel tunedModel = new PlayerDamageModel();
        tunedModel.registerAdrenalineNearMiss(player, 0.35F, 0);
        helper.assertTrue(Math.abs(tunedModel.getSuppressionIntensity() - 0.08736F) < 0.0001F, "admin gain coefficient must change accumulation");
        FirstAid.suppressionGainMultiplier = configuredGain;
        FirstAid.lowSuppressionEnabled = false;
        helper.assertTrue(Math.abs(FirstAid.suppressionDisplayCurve(0.5F) - 0.5F) < 0.0001F, "dynamic curve must be linear");
        FirstAid.lowSuppressionEnabled = true;
        helper.assertTrue(Math.abs(FirstAid.suppressionDisplayCurve(0.5F * FirstAid.lowSuppressionMultiplier) - 0.5F * FirstAid.lowSuppressionMultiplier) < 0.0001F, "mild curve must be linearly scaled");
        FirstAid.lowSuppressionEnabled = configuredMild;
        while (model.getSuppressionIntensity() > 0.0F) model.tickSuppressionState();
        helper.assertValueEqual(model.getSuppressionPainReliefTicks(), 20, "pain relief starts at 20 ticks when suppression reaches zero");
        for (int i = 0; i < 19; i++) model.tickSuppressionState();
        helper.assertTrue(model.isPainSuppressed(player), "pain relief must survive tick 19");
        model.tickSuppressionState();
        helper.assertTrue(!model.isPainSuppressed(player), "pain relief must expire on tick 20");

        model.applyTrackedInjuryEffect(player, "minecraft:slowness", new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
        helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "tracked injury effect must be applied");
        model.registerAdrenalineNearMiss(player, 0.35F, 0);
        helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "pain relief must immediately clear owned injury effects");
        model.applyTrackedInjuryEffect(player, "minecraft:slowness", new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
        helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "pain relief must prevent injury effect reapplication");

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
            float beforeAttack = attackModel.getSuppressionIntensity();
            zombie.hurtServer(helper.getLevel(), melee, 2.0F);
            helper.assertTrue(attackModel.getSuppressionIntensity() > beforeAttack, "actual enemy damage adds suppression");
            helper.assertTrue(EventHandler.isEligibleOffensiveDamage(wolf, melee, 2.0F), "neutral melee qualifies");
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(cow, melee, 2.0F), "passive target excluded");
            FakePlayer otherPlayer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.fromString("f175a1d0-e14e-4b4a-b91a-2f06fa19c2ef"), "[FirstAidTarget]"));
            helper.assertTrue(EventHandler.isEligibleOffensiveDamage(otherPlayer, melee, 2.0F), "other player target qualifies");
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(player, melee, 2.0F), "self damage excluded");
            float beforePvP = attackModel.getSuppressionIntensity();
            PlayerDamageModel targetModel = (PlayerDamageModel) CommonUtils.getDamageModel(otherPlayer);
            float targetBeforePvP = targetModel.getSuppressionIntensity();
            helper.assertTrue(EventHandler.handleCustomPlayerDamage(otherPlayer, melee, 2.0F), "PvP body-part damage must be handled");
            helper.assertTrue(attackModel.getSuppressionIntensity() > beforePvP, "actual player damage adds adrenaline");
            helper.assertTrue(targetModel.getSuppressionIntensity() > targetBeforePvP, "damaged player also gains adrenaline");
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(zombie, melee, 0.0F), "zero damage excluded");
            helper.assertTrue(EventHandler.isEligibleOffensiveDamage(zombie, helper.getLevel().damageSources().arrow(arrow, player), 2.0F), "owned projectile qualifies");
            arrow.setOwner(wolf);
            helper.assertTrue(!EventHandler.isEligibleOffensiveDamage(zombie, helper.getLevel().damageSources().arrow(arrow, player), 2.0F), "unowned projectile excluded");
            float invalidAttackerGain = attackModel.getSuppressionIntensity();
            float invalidVictimGain = targetModel.getSuppressionIntensity();
            EventHandler.handleCustomPlayerDamage(otherPlayer, helper.getLevel().damageSources().arrow(arrow, player), 1.0F);
            helper.assertTrue(attackModel.getSuppressionIntensity() == invalidAttackerGain
                && targetModel.getSuppressionIntensity() == invalidVictimGain, "unowned projectile gives neither player adrenaline");
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
        helper.assertTrue(player.hasEffect(MobEffects.HASTE), "medium adrenaline grants Haste I");
        helper.assertTrue(!player.hasEffect(MobEffects.STRENGTH), "medium adrenaline does not grant Strength");
        encounterModel.ensureEncounterAdrenaline(player, 0.72F);
        encounterModel.tick(helper.getLevel(), player);
        helper.assertTrue(player.hasEffect(MobEffects.STRENGTH), "high adrenaline grants Strength I");
        player.addEffect(new MobEffectInstance(MobEffects.HASTE, 200, 1));
        encounterModel.tick(helper.getLevel(), player);
        helper.assertValueEqual(player.getEffect(MobEffects.HASTE).getAmplifier(), 1, "stronger external haste survives");
        player.removeAllEffects();
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
            helper.assertTrue(player.hasEffect(MobEffects.HASTE) && player.hasEffect(MobEffects.STRENGTH), "rescued player receives injection effects");
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
    }
}
