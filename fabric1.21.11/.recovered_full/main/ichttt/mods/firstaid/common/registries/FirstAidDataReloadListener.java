/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.DynamicOps
 *  com.mojang.serialization.JsonOps
 *  net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
 *  net.minecraft.resources.FileToIdConverter
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.packs.resources.ResourceManager
 *  net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
 *  net.minecraft.server.packs.resources.SimplePreparableReloadListener
 *  net.minecraft.util.profiling.ProfilerFiller
 */
package ichttt.mods.firstaid.common.registries;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import ichttt.mods.firstaid.api.debuff.IDebuffBuilder;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import ichttt.mods.firstaid.common.registries.FirstAidBaseCodecs;
import ichttt.mods.firstaid.common.registries.FirstAidRegistryLookups;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class FirstAidDataReloadListener
extends SimplePreparableReloadListener<Data>
implements IdentifiableResourceReloadListener {
    private static final FileToIdConverter DAMAGE_LISTER = FileToIdConverter.json((String)"firstaid/damage_distributions");
    private static final FileToIdConverter DEBUFF_LISTER = FileToIdConverter.json((String)"firstaid/debuffs");
    private static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath((String)"firstaid", (String)"data_reload");

    protected Data prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        HashMap<Identifier, IDamageDistributionTarget> damageTargets = new HashMap<Identifier, IDamageDistributionTarget>();
        HashMap<Identifier, IDebuffBuilder> debuffBuilders = new HashMap<Identifier, IDebuffBuilder>();
        SimpleJsonResourceReloadListener.scanDirectory((ResourceManager)resourceManager, (FileToIdConverter)DAMAGE_LISTER, (DynamicOps)JsonOps.INSTANCE, FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_TARGETS_DIRECT_CODEC, damageTargets);
        SimpleJsonResourceReloadListener.scanDirectory((ResourceManager)resourceManager, (FileToIdConverter)DEBUFF_LISTER, (DynamicOps)JsonOps.INSTANCE, FirstAidBaseCodecs.DEBUFF_BUILDERS_DIRECT_CODEC, debuffBuilders);
        return new Data(damageTargets, debuffBuilders);
    }

    protected void apply(Data data, ResourceManager resourceManager, ProfilerFiller profiler) {
        FirstAidRegistryLookups.updateData(data.damageTargets, data.debuffBuilders);
    }

    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    record Data(Map<Identifier, IDamageDistributionTarget> damageTargets, Map<Identifier, IDebuffBuilder> debuffBuilders) {
    }
}

