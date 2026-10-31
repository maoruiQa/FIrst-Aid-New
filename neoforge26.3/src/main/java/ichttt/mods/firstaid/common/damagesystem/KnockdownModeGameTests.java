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

package ichttt.mods.firstaid.common.damagesystem;

import com.mojang.serialization.MapCodec;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.KnockdownRules;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.DirectDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class KnockdownModeGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(FirstAid.MODID, "knockdown_mode_regression");

    private KnockdownModeGameTests() {
    }

    public static void register(IEventBus bus) {
        DeferredRegister<MapCodec<? extends GameTestInstance>> types = DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, FirstAid.MODID);
        types.register("knockdown_mode_regression", () -> RegressionTest.CODEC);
        types.register(bus);
        bus.addListener(KnockdownModeGameTests::onRegister);
    }

    private static void onRegister(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(ID, new TestEnvironmentDefinition.AllOf());
        event.registerTest(ID, new RegressionTest(new TestData<>(environment, Identifier.parse("minecraft:empty"), 100, 0, true)));
    }

    private static final class RegressionTest extends GameTestInstance {
        private static final MapCodec<RegressionTest> CODEC = TestData.CODEC.xmap(RegressionTest::new, RegressionTest::info);
        private RegressionTest(TestData<Holder<TestEnvironmentDefinition<?>>> data) { super(data); }
        @Override public void run(GameTestHelper helper) { KnockdownModeGameTests.run(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("First Aid knockdown mode regression"); }
    }

    private static void run(GameTestHelper helper) {
        FirstAid.KnockdownMode savedMode = FirstAid.knockdownMode;
        double savedCoefficient = FirstAid.knockdownOverflowCoefficient;
        try {
            helper.assertTrue(savedMode == FirstAid.KnockdownMode.CHANCE, "knockdown mode must default to CHANCE");
            helper.assertTrue(Math.abs(savedCoefficient - FirstAid.DEFAULT_KNOCKDOWN_OVERFLOW_COEFFICIENT) < 1.0E-9D, "knockdown coefficient must default to 0.35");

            Scenario always = hitHead(helper, FirstAid.KnockdownMode.ALWAYS, 0.35D, null, 40.0F);
            helper.assertTrue(always.downed() && always.alive(), "ALWAYS must down the player even after a huge overkill hit");
            helper.assertTrue(((DamageablePart) always.model().HEAD).getKnockdownOverflow() == 0.0F, "overflow tracking must be consumed by the knockdown decision");

            Scenario never = hitHead(helper, FirstAid.KnockdownMode.NEVER, 0.35D, null, 0.0F);
            helper.assertTrue(!never.downed() && never.dead(), "NEVER must kill the player when the head reaches 0 HP");

            Scenario exact = hitHead(helper, FirstAid.KnockdownMode.CHANCE, 0.35D, 0.999F, 0.0F);
            helper.assertTrue(exact.downed() && exact.alive(), "CHANCE without overflow must always down the player");

            double chanceAtOneMax = KnockdownRules.chanceForOverflowRatio(1.0D, 0.35D);
            Scenario lucky = hitHead(helper, FirstAid.KnockdownMode.CHANCE, 0.35D, (float) chanceAtOneMax - 0.01F, 1.0F);
            helper.assertTrue(lucky.downed() && lucky.alive(), "CHANCE roll below exp(-0.35) must down the player at one max-health of overflow");
            Scenario unlucky = hitHead(helper, FirstAid.KnockdownMode.CHANCE, 0.35D, (float) chanceAtOneMax + 0.01F, 1.0F);
            helper.assertTrue(!unlucky.downed() && unlucky.dead(), "CHANCE roll above exp(-0.35) must kill the player at one max-health of overflow");

            Scenario zeroCoefficient = hitHead(helper, FirstAid.KnockdownMode.CHANCE, 0.0D, 0.999F, 40.0F);
            helper.assertTrue(zeroCoefficient.downed(), "a coefficient of 0 must always down the player");
            Scenario harsh = hitHead(helper, FirstAid.KnockdownMode.CHANCE, FirstAid.MAX_KNOCKDOWN_OVERFLOW_COEFFICIENT, 0.01F, 10.0F);
            helper.assertTrue(harsh.dead(), "a maximum coefficient with heavy overflow must kill on an ordinary roll");

            ServerPlayer bothPlayer = freshPlayer(helper);
            PlayerDamageModel bothModel = (PlayerDamageModel) CommonUtils.getDamageModel(bothPlayer);
            FirstAid.knockdownMode = FirstAid.KnockdownMode.ALWAYS;
            bothModel.BODY.currentHealth = 0.0F;
            damageHead(bothPlayer, bothModel, bothModel.HEAD.getMaxHealth());
            helper.assertTrue(!bothModel.isCriticalDowned() && bothModel.isDead(bothPlayer), "losing head and body together must stay fatal in ALWAYS mode");

            Scenario switched = hitHead(helper, FirstAid.KnockdownMode.ALWAYS, 0.35D, null, 0.0F);
            FirstAid.knockdownMode = FirstAid.KnockdownMode.NEVER;
            DamageDistribution.handleDamageTaken(new DirectDamageDistributionAlgorithm(EnumPlayerPart.LEFT_ARM, false), switched.model(), 1.0F, switched.player(), switched.player().damageSources().generic(), false, false);
            helper.assertTrue(switched.model().isCriticalDowned() && !switched.model().isDead(switched.player()), "switching to NEVER must not kill players who are already down");

            helper.assertTrue(KnockdownRules.fingerprint(FirstAid.KnockdownMode.ALWAYS, 0.1D).equals(KnockdownRules.fingerprint(FirstAid.KnockdownMode.ALWAYS, 3.0D)),
                    "the coefficient must not change the notice fingerprint outside CHANCE mode");
            helper.assertTrue(!KnockdownRules.fingerprint(FirstAid.KnockdownMode.CHANCE, 0.35D).equals(KnockdownRules.fingerprint(FirstAid.KnockdownMode.CHANCE, 0.5D)),
                    "the coefficient must change the notice fingerprint in CHANCE mode");
            helper.succeed();
        } finally {
            FirstAid.knockdownMode = savedMode;
            FirstAid.knockdownOverflowCoefficient = savedCoefficient;
            KnockdownRules.setForcedRollForTesting(null);
        }
    }

    private record Scenario(ServerPlayer player, PlayerDamageModel model) {
        boolean downed() {
            return model.isCriticalDowned();
        }

        boolean alive() {
            return player.isAlive() && !model.isDead(player);
        }

        boolean dead() {
            return model.isDead(player);
        }
    }

    /**
     * Hits a fresh player's full head with its max health plus {@code overflowMultiples} times that max health.
     */
    private static Scenario hitHead(GameTestHelper helper, FirstAid.KnockdownMode mode, double coefficient, Float roll, float overflowMultiples) {
        FirstAid.knockdownMode = mode;
        FirstAid.knockdownOverflowCoefficient = coefficient;
        KnockdownRules.setForcedRollForTesting(roll);
        ServerPlayer player = freshPlayer(helper);
        PlayerDamageModel model = (PlayerDamageModel) CommonUtils.getDamageModel(player);
        float maxHealth = model.HEAD.getMaxHealth();
        damageHead(player, model, maxHealth * (1.0F + overflowMultiples));
        KnockdownRules.setForcedRollForTesting(null);
        return new Scenario(player, model);
    }

    private static void damageHead(ServerPlayer player, PlayerDamageModel model, float amount) {
        DamageDistribution.handleDamageTaken(new DirectDamageDistributionAlgorithm(EnumPlayerPart.HEAD, false), model, amount, player, player.damageSources().generic(), false, false);
    }

    private static ServerPlayer freshPlayer(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.connection = FakePlayerFactory.getMinecraft(helper.getLevel()).connection;
        return player;
    }
}
