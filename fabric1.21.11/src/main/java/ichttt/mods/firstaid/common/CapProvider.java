package ichttt.mods.firstaid.common;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;

public final class CapProvider {
   public static final Identifier DAMAGE_MODEL_ID = Identifier.fromNamespaceAndPath("firstaid", "damage_model");
   public static final Set<String> tutorialDone = new HashSet<>();

   private CapProvider() {
   }
}
