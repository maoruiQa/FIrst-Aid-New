package ichttt.mods.firstaid.fabric;

import ichttt.mods.firstaid.FirstAid;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3675;
import net.minecraft.class_437;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

public class FirstAidFabricClientEntrypoint implements ClientModInitializer {
    private static final class_2960 BODY_HUD_TEXTURE = class_2960.method_60655(FirstAid.MODID, "textures/gui/simple_health.png");
    private static boolean hudVisible = true;
    private static class_304 showWoundsKey;
    private static final SegmentedHealthModel HEALTH_MODEL = new SegmentedHealthModel();

    @Override
    public void onInitializeClient() {
        FirstAid.LOGGER.info("FirstAid Fabric client initialized");

        showWoundsKey = KeyBindingHelper.registerKeyBinding(new class_304(
                "keybinds.show_wounds",
                class_3675.class_307.field_1668,
                GLFW.GLFW_KEY_H,
                class_304.class_11900.field_62556
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.field_1724 != null) {
                HEALTH_MODEL.syncToPlayer(client.field_1724.method_6032(), client.field_1724.method_6063());
            }
            while (showWoundsKey.method_1436()) {
                hudVisible = !hudVisible;
                if (client.field_1724 != null) {
                    client.field_1724.method_7353(class_2561.method_43471(hudVisible ? "firstaid.hud.enabled" : "firstaid.hud.disabled"), true);
                }
                client.method_1507(new BodyHealthScreen());
            }
        });

        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            if (!hudVisible) {
                return;
            }
            drawContext.method_25294(8, 8, 124, 70, 0x90000000);
            drawContext.method_70845(BODY_HUD_TEXTURE, 12, 12, 0, 0, 48, 48, 128, 128);
            drawContext.method_51439(class_310.method_1551().field_1772, class_2561.method_43470("First Aid"), 64, 14, 0xFFFFFF, false);
            drawContext.method_51439(class_310.method_1551().field_1772, class_2561.method_43470("Total: " + HEALTH_MODEL.totalText()), 64, 27, 0xE0E0E0, false);
            drawContext.method_51439(class_310.method_1551().field_1772, class_2561.method_43470("H  Open body screen"), 64, 40, 0xCFCFCF, false);
            drawContext.method_25294(64, 54, 116, 62, 0x80262626);
            drawContext.method_25294(64, 54, 64 + HEALTH_MODEL.totalBarWidth(52), 62, HEALTH_MODEL.totalColor());
        });
    }

    private static final class BodyHealthScreen extends class_437 {
        private BodyHealthScreen() {
            super(class_2561.method_43471("firstaid.gui.healthscreen"));
        }

        @Override
        public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
            context.method_25294(0, 0, this.field_22789, this.field_22790, 0x6A000000);
            int x = (this.field_22789 - 220) / 2;
            int y = (this.field_22790 - 168) / 2;
            context.method_25294(x, y, x + 220, y + 168, 0xD0141414);
            context.method_70845(BODY_HUD_TEXTURE, x + 10, y + 10, 0, 0, 64, 64, 128, 128);
            context.method_51439(this.field_22793, class_2561.method_43471("firstaid.gui.healthscreen"), x + 82, y + 14, 0xFFFFFF, false);
            renderLimb(context, x + 82, y + 34, "Head", Limb.HEAD);
            renderLimb(context, x + 82, y + 49, "Body", Limb.BODY);
            renderLimb(context, x + 82, y + 64, "L Arm", Limb.LEFT_ARM);
            renderLimb(context, x + 82, y + 79, "R Arm", Limb.RIGHT_ARM);
            renderLimb(context, x + 82, y + 94, "L Leg", Limb.LEFT_LEG);
            renderLimb(context, x + 82, y + 109, "R Leg", Limb.RIGHT_LEG);
            context.method_51439(this.field_22793, class_2561.method_43470("Total " + HEALTH_MODEL.totalText()), x + 10, y + 132, 0xFFFFFF, false);
            context.method_51439(this.field_22793, class_2561.method_43470("Press ESC to close"), x + 10, y + 148, 0xBFBFBF, false);
            super.method_25394(context, mouseX, mouseY, delta);
        }

        private void renderLimb(class_332 context, int x, int y, String label, Limb limb) {
            context.method_51439(this.field_22793, class_2561.method_43470(label + " " + HEALTH_MODEL.partText(limb)), x, y, 0xE5E5E5, false);
            context.method_25294(x + 92, y + 1, x + 130, y + 8, 0x90222222);
            context.method_25294(x + 92, y + 1, x + 92 + HEALTH_MODEL.partBarWidth(limb, 38), y + 8, HEALTH_MODEL.partColor(limb));
        }

        @Override
        public boolean method_25421() {
            return false;
        }
    }

    private enum Limb {
        HEAD(0.15F),
        BODY(0.35F),
        LEFT_ARM(0.125F),
        RIGHT_ARM(0.125F),
        LEFT_LEG(0.125F),
        RIGHT_LEG(0.125F);

        private final float weight;

        Limb(float weight) {
            this.weight = weight;
        }
    }

    private static final class SegmentedHealthModel {
        private final EnumMap<Limb, Float> maxByPart = new EnumMap<>(Limb.class);
        private final EnumMap<Limb, Float> healthByPart = new EnumMap<>(Limb.class);
        private float lastTotalHealth = -1.0F;
        private float lastMaxHealth = -1.0F;
        private int damageCursor;

        private SegmentedHealthModel() {
            initializeFromMax(20.0F);
            this.lastTotalHealth = totalHealth();
            this.lastMaxHealth = totalMax();
        }

        private void syncToPlayer(float playerHealth, float playerMaxHealth) {
            if (Math.abs(playerMaxHealth - this.lastMaxHealth) > 0.01F) {
                initializeFromMax(playerMaxHealth);
                this.lastMaxHealth = playerMaxHealth;
                this.lastTotalHealth = totalHealth();
            }

            if (this.lastTotalHealth < 0.0F) {
                this.lastTotalHealth = playerHealth;
            }

            float delta = playerHealth - this.lastTotalHealth;
            if (delta < -0.01F) {
                applyDamage(-delta);
            } else if (delta > 0.01F) {
                applyHealing(delta);
            }
            this.lastTotalHealth = playerHealth;
        }

        private void initializeFromMax(float totalMax) {
            float remaining = totalMax;
            Limb[] limbs = Limb.values();
            for (int i = 0; i < limbs.length; i++) {
                Limb limb = limbs[i];
                float partMax = i == limbs.length - 1 ? remaining : Math.max(1.0F, totalMax * limb.weight);
                partMax = Math.min(partMax, remaining);
                this.maxByPart.put(limb, partMax);
                this.healthByPart.put(limb, partMax);
                remaining -= partMax;
            }
        }

        private void applyDamage(float amount) {
            while (amount > 0.001F && totalHealth() > 0.001F) {
                Limb target = pickDamageTarget();
                float current = this.healthByPart.get(target);
                if (current <= 0.0F) {
                    continue;
                }
                float dealt = Math.min(current, amount);
                this.healthByPart.put(target, current - dealt);
                amount -= dealt;
            }
        }

        private Limb pickDamageTarget() {
            this.damageCursor++;
            int bucket = this.damageCursor % 10;
            if (bucket < 4) {
                return Limb.BODY;
            }
            if (bucket < 6) {
                return Limb.HEAD;
            }
            if (bucket == 6) {
                return Limb.LEFT_ARM;
            }
            if (bucket == 7) {
                return Limb.RIGHT_ARM;
            }
            if (bucket == 8) {
                return Limb.LEFT_LEG;
            }
            return Limb.RIGHT_LEG;
        }

        private void applyHealing(float amount) {
            while (amount > 0.001F) {
                Limb target = findMostDamagedLimb();
                float current = this.healthByPart.get(target);
                float max = this.maxByPart.get(target);
                if (current >= max - 0.001F) {
                    return;
                }
                float healed = Math.min(max - current, amount);
                this.healthByPart.put(target, current + healed);
                amount -= healed;
            }
        }

        private Limb findMostDamagedLimb() {
            List<Limb> priority = new ArrayList<>(List.of(Limb.HEAD, Limb.BODY, Limb.LEFT_ARM, Limb.RIGHT_ARM, Limb.LEFT_LEG, Limb.RIGHT_LEG));
            priority.sort(Comparator.comparingDouble(this::partPercent));
            return priority.get(0);
        }

        private float totalHealth() {
            float total = 0.0F;
            for (Limb limb : Limb.values()) {
                total += this.healthByPart.get(limb);
            }
            return total;
        }

        private float totalMax() {
            float total = 0.0F;
            for (Limb limb : Limb.values()) {
                total += this.maxByPart.get(limb);
            }
            return total;
        }

        private String totalText() {
            return String.format("%.1f/%.1f", totalHealth(), totalMax());
        }

        private String partText(Limb limb) {
            return String.format("%.0f%%", partPercent(limb) * 100.0F);
        }

        private int totalBarWidth(int maxPixels) {
            return Math.max(0, Math.min(maxPixels, Math.round((totalHealth() / Math.max(totalMax(), 0.001F)) * maxPixels)));
        }

        private int partBarWidth(Limb limb, int maxPixels) {
            return Math.max(0, Math.min(maxPixels, Math.round(partPercent(limb) * maxPixels)));
        }

        private float partPercent(Limb limb) {
            float max = Math.max(this.maxByPart.get(limb), 0.001F);
            return this.healthByPart.get(limb) / max;
        }

        private int totalColor() {
            return gradientColor(totalHealth() / Math.max(totalMax(), 0.001F));
        }

        private int partColor(Limb limb) {
            return gradientColor(partPercent(limb));
        }

        private int gradientColor(float percent) {
            float clamped = Math.max(0.0F, Math.min(1.0F, percent));
            int red = (int) ((1.0F - clamped) * 220.0F + 40.0F);
            int green = (int) (clamped * 190.0F + 40.0F);
            int blue = 40;
            return 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
    }
}
