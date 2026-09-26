package ichttt.mods.firstaid.client.util;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public final class HeartSpriteHelper {
    private HeartSpriteHelper() {
    }

    public static Identifier container(Player player, boolean blinking) {
        String name = player.level().getLevelData().isHardcore() ? "container_hardcore" : "container";
        return sprite(name + (blinking ? "_blinking" : ""));
    }

    public static Identifier heart(Player player, boolean absorbing, boolean half, boolean blinking) {
        boolean hardcore = player.level().getLevelData().isHardcore();
        String type = absorbing ? "absorbing" : player.hasEffect(MobEffects.POISON) ? "poisoned" : player.hasEffect(MobEffects.WITHER) ? "withered" : player.isFullyFrozen() ? "frozen" : "";
        StringBuilder name = new StringBuilder();
        if (!type.isEmpty()) name.append(type).append('_');
        if (hardcore) name.append("hardcore_");
        name.append(half ? "half" : "full");
        if (blinking) name.append("_blinking");
        return sprite(name.toString());
    }

    private static Identifier sprite(String name) {
        return Identifier.withDefaultNamespace("hud/heart/" + name);
    }
}
