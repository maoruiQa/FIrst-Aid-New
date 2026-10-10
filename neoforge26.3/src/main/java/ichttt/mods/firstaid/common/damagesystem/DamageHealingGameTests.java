package ichttt.mods.firstaid.common.damagesystem;

import com.mojang.serialization.MapCodec;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

public final class DamageHealingGameTests {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(FirstAid.MODID, "damage_healing_regression");

    private DamageHealingGameTests() {}

    public static void register(IEventBus bus) {
        DeferredRegister<MapCodec<? extends GameTestInstance>> types = DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, FirstAid.MODID);
        types.register("damage_healing_regression", () -> RegressionTest.CODEC);
        types.register(bus);
        bus.addListener(DamageHealingGameTests::onRegister);
    }

    private static void onRegister(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(ID, new TestEnvironmentDefinition.AllOf());
        event.registerTest(ID, new RegressionTest(new TestData<>(environment, Identifier.parse("minecraft:empty"), 100, 0, true)));
    }

    private static final class RegressionTest extends GameTestInstance {
        private static final MapCodec<RegressionTest> CODEC = TestData.CODEC.xmap(RegressionTest::new, RegressionTest::info);
        private RegressionTest(TestData<Holder<TestEnvironmentDefinition<?>>> data) { super(data); }
        @Override public void run(GameTestHelper helper) { DamageHealingGameTests.run(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("First Aid damage and healing regression"); }
    }

    private static void run(GameTestHelper helper) {
        var player = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.connection = FakePlayerFactory.getMinecraft(helper.getLevel()).connection;
        PlayerDamageModel model = (PlayerDamageModel) CommonUtils.getDamageModel(player);
        var saved = model.serializeNBT();
        boolean allowHealing = FirstAidConfig.SERVER.allowOtherHealingItems.get();
        double healingMultiplier = FirstAidConfig.SERVER.otherRegenMultiplier.get();
        try {
            model.clearStatusEffects();
            for (AbstractDamageablePart part : model) part.currentHealth = 0.0F;
            model.HEAD.currentHealth = model.HEAD.getMaxHealth();
            var feet = new StandardDamageDistributionAlgorithm(Map.of(EquipmentSlot.FEET, List.of(EnumPlayerPart.LEFT_FOOT)), false, true);
            float before = model.HEAD.currentHealth;
            feet.distributeDamage(2.0F, player, player.damageSources().lava(), false);
            helper.assertTrue(Math.abs(before - model.HEAD.currentHealth - 1.0F) < 0.0001F,
                    "foot -> empty leg -> empty body -> head must retain half the incoming damage");

            for (AbstractDamageablePart part : model) part.currentHealth = 0.0F;
            model.LEFT_LEG.currentHealth = 0.25F;
            model.HEAD.currentHealth = model.HEAD.getMaxHealth();
            before = model.HEAD.currentHealth;
            feet.distributeDamage(2.0F, player, player.damageSources().lava(), false);
            helper.assertTrue(Math.abs(before - model.HEAD.currentHealth - 0.84375F) < 0.0001F,
                    "partially consumed overflow must retain half of its original-scale remainder");

            helper.assertTrue(EventHandler.getForcedDamageDistribution(player.damageSources().enderPearl()) == RandomDamageDistributionAlgorithm.PER_POINT,
                    "pearl teleport damage must select per-point distribution");
            for (int attempt = 0; attempt < 128; attempt++) {
                for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
                before = totalHealth(model);
                RandomDamageDistributionAlgorithm.PER_POINT.distributeDamage(2.0F, player, player.damageSources().enderPearl(), false);
                helper.assertTrue(Math.abs(before - totalHealth(model) - 2.0F) < 0.0001F, "pearl points must conserve total damage");
            }

            FirstAidConfig.SERVER.allowOtherHealingItems.set(true);
            FirstAidConfig.SERVER.otherRegenMultiplier.set(0.75D);
            for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth() - 1.25F;
            CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(player.getMaxHealth()));
            before = totalHealth(model);
            float visibleBefore = visibleHealth(model);
            var regeneration = new MobEffectInstance(MobEffects.REGENERATION, 200, 0);
            for (int tick = 0; tick < 200; tick++) regeneration.tickServer(helper.getLevel(), player, () -> {});
            helper.assertTrue(Math.abs(totalHealth(model) - before - 3.0F) < 0.0001F, "ten seconds of regeneration I must restore all four scaled pulses even at full vanilla health");
            helper.assertTrue(visibleHealth(model) > visibleBefore, "short regeneration must produce a visible HUD increase");

            for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
            model.LEFT_ARM.currentHealth -= 0.10F;
            model.RIGHT_ARM.currentHealth -= 2.0F;
            before = totalHealth(model);
            HealthDistribution.addRandomHealth(0.75F, player, false);
            helper.assertTrue(Math.abs(totalHealth(model) - before - 0.75F) < 0.0001F, "full and nearly full parts must not waste healing");
            FirstAidConfig.SERVER.allowOtherHealingItems.set(false);
            before = totalHealth(model);
            MobEffects.REGENERATION.value().applyEffectTick(helper.getLevel(), player, 0);
            helper.assertTrue(Math.abs(totalHealth(model) - before) < 0.0001F, "disabled external healing must remain disabled");

            model.deserializeNBT(saved);
            model.clearStatusEffects();
            for (AbstractDamageablePart part : model) part.currentHealth = part.getMaxHealth();
            CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(player.getMaxHealth()));
            int lavaHits = 0;
            while (lavaHits < 32 && !model.isDead(player)) {
                EventHandler.handleCustomPlayerDamage(player, player.damageSources().lava(), 4.0F);
                lavaHits++;
            }
            helper.assertTrue(model.isDead(player), "unarmored repeated lava damage must remain lethal within 32 hits");
            FirstAid.LOGGER.info("Damage/healing regression: unarmored lava became lethal after {} hits", lavaHits);
            helper.succeed();
        } finally {
            FirstAidConfig.SERVER.allowOtherHealingItems.set(allowHealing);
            FirstAidConfig.SERVER.otherRegenMultiplier.set(healingMultiplier);
            model.deserializeNBT(saved);
            player.removeAllEffects();
        }
    }

    private static float totalHealth(PlayerDamageModel model) {
        float total = 0.0F;
        for (AbstractDamageablePart part : model) total += part.currentHealth;
        return total;
    }

    private static float visibleHealth(PlayerDamageModel model) {
        float total = 0.0F;
        for (AbstractDamageablePart part : model) total += (float) Math.ceil(CommonUtils.getVisualHealth(part));
        return total;
    }
}
