package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public final class StatusSummaryRenderer {
    private StatusSummaryRenderer() {
    }

    public static int renderStatusSummary(
            GuiGraphicsExtractor guiGraphics,
            Font font,
            Player player,
            AbstractPlayerDamageModel damageModel,
            @Nullable PlayerDamageModel playerDamageModel,
            int baseX,
            int baseY
    ) {
        int lineY = baseY;
        int painLevel = damageModel.getPainLevel();
        if (painLevel > 0) {
            boolean painSuppressed = player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
            Component painText = painSuppressed
                    ? Component.translatable("firstaid.gui.status.pain_suppressed")
                    : Component.translatable("firstaid.gui.status.pain", Component.translatable(getPainSeverityKey(painLevel)));
            guiGraphics.text(font, painText, baseX, lineY, opaque(painSuppressed ? 9425919 : 16747146));
            lineY += 10;
        }

        int adrenalineLevel = damageModel.getAdrenalineLevel();
        if (adrenalineLevel > 0) {
            int suppressionLevel = playerDamageModel != null ? playerDamageModel.getSuppressionLevel() : adrenalineLevel;
            guiGraphics.text(
                    font,
                    Component.translatable("firstaid.gui.status.suppression", Component.translatable(getSuppressionSeverityKey(suppressionLevel))),
                    baseX,
                    lineY,
                    opaque(12637930)
            );
            lineY += 10;
        }

        for (MedicineStatusDisplay display : MedicineStatusClientHelper.collect(player)) {
            lineY = MedicineStatusClientHelper.drawStatusLine(guiGraphics, font, display, baseX, lineY);
        }

        return lineY;
    }

    public static int countVisibleLines(AbstractPlayerDamageModel damageModel, Player player) {
        int count = 0;
        int painLevel = damageModel.getPainLevel();
        if (painLevel > 0) {
            count++;
        }
        int adrenalineLevel = damageModel.getAdrenalineLevel();
        if (adrenalineLevel > 0) {
            count++;
        }
        for (MedicineStatusDisplay display : MedicineStatusClientHelper.collect(player)) {
            count++;
        }
        return count;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static String getPainSeverityKey(int painLevel) {
        return switch (painLevel) {
            case 1 -> "firstaid.gui.pain.mild";
            case 2 -> "firstaid.gui.pain.moderate";
            case 3 -> "firstaid.gui.pain.severe";
            case 4 -> "firstaid.gui.pain.extreme";
            default -> "firstaid.gui.pain.critical";
        };
    }

    private static String getSuppressionSeverityKey(int suppressionLevel) {
        return switch (suppressionLevel) {
            case 1 -> "firstaid.gui.suppression.low";
            case 2 -> "firstaid.gui.suppression.medium";
            default -> "firstaid.gui.suppression.high";
        };
    }
}
