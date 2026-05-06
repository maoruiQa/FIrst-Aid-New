/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableList$Builder
 *  javax.annotation.Nonnull
 *  net.minecraft.util.StringRepresentable
 *  net.minecraft.world.entity.EquipmentSlot
 */
package ichttt.mods.firstaid.api.enums;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;

public enum EnumPlayerPart implements StringRepresentable
{
    HEAD(EquipmentSlot.HEAD),
    LEFT_ARM(EquipmentSlot.CHEST),
    LEFT_LEG(EquipmentSlot.LEGS),
    LEFT_FOOT(EquipmentSlot.FEET),
    BODY(EquipmentSlot.CHEST),
    RIGHT_ARM(EquipmentSlot.CHEST),
    RIGHT_LEG(EquipmentSlot.LEGS),
    RIGHT_FOOT(EquipmentSlot.FEET);

    public static final EnumPlayerPart[] VALUES;
    private ImmutableList<EnumPlayerPart> neighbours;
    private final String serializedName;
    public final EquipmentSlot slot;

    private EnumPlayerPart(EquipmentSlot slot) {
        this.slot = slot;
        this.serializedName = this.name().toLowerCase(Locale.ROOT);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public ImmutableList<EnumPlayerPart> getNeighbours() {
        if (this.neighbours == null) {
            EnumPlayerPart enumPlayerPart = this;
            synchronized (enumPlayerPart) {
                if (this.neighbours == null) {
                    ImmutableList builtList;
                    ImmutableList.Builder builder = ImmutableList.builder();
                    builder.addAll(this.getNeighboursDown());
                    builder.addAll(this.getNeighboursUp());
                    builder.addAll(this.getNeighboursLeft());
                    builder.addAll(this.getNeighboursRight());
                    this.neighbours = builtList = builder.build();
                    return builtList;
                }
            }
        }
        return this.neighbours;
    }

    @Nonnull
    private List<EnumPlayerPart> getNeighboursUp() {
        switch (this.ordinal()) {
            case 4: {
                return Collections.singletonList(HEAD);
            }
            case 2: 
            case 6: {
                return Collections.singletonList(BODY);
            }
            case 3: {
                return Collections.singletonList(LEFT_LEG);
            }
            case 7: {
                return Collections.singletonList(RIGHT_LEG);
            }
        }
        return Collections.emptyList();
    }

    @Nonnull
    private List<EnumPlayerPart> getNeighboursDown() {
        switch (this.ordinal()) {
            case 0: {
                return Collections.singletonList(BODY);
            }
            case 4: {
                return Arrays.asList(LEFT_LEG, RIGHT_LEG);
            }
            case 2: {
                return Collections.singletonList(LEFT_FOOT);
            }
            case 6: {
                return Collections.singletonList(RIGHT_FOOT);
            }
        }
        return Collections.emptyList();
    }

    @Nonnull
    private List<EnumPlayerPart> getNeighboursLeft() {
        switch (this.ordinal()) {
            case 5: {
                return Collections.singletonList(BODY);
            }
            case 6: {
                return Collections.singletonList(LEFT_LEG);
            }
            case 7: {
                return Collections.singletonList(LEFT_FOOT);
            }
            case 4: {
                return Collections.singletonList(LEFT_ARM);
            }
        }
        return Collections.emptyList();
    }

    @Nonnull
    private List<EnumPlayerPart> getNeighboursRight() {
        switch (this.ordinal()) {
            case 1: {
                return Collections.singletonList(BODY);
            }
            case 2: {
                return Collections.singletonList(RIGHT_LEG);
            }
            case 3: {
                return Collections.singletonList(RIGHT_FOOT);
            }
            case 4: {
                return Collections.singletonList(RIGHT_ARM);
            }
        }
        return Collections.emptyList();
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    static {
        for (EnumPlayerPart value : VALUES = EnumPlayerPart.values()) {
            ImmutableList<EnumPlayerPart> neighbours = value.getNeighbours();
            if (neighbours.contains((Object)value)) {
                throw new RuntimeException(String.valueOf((Object)value) + " contains itself as a neighbour!");
            }
            if (neighbours.isEmpty()) {
                throw new RuntimeException(String.valueOf((Object)value) + " does not have any neighbours!");
            }
            if (EnumSet.copyOf(neighbours).size() != neighbours.size()) {
                throw new RuntimeException(String.valueOf((Object)value) + " neighbours contain the same part multiple times!");
            }
            EnumSet<EnumPlayerPart> hopefullyAllParts = EnumSet.copyOf(neighbours);
            int oldSize = -1;
            while (oldSize != hopefullyAllParts.size()) {
                oldSize = hopefullyAllParts.size();
                EnumSet<EnumPlayerPart> neighboursOfNeighbours = EnumSet.noneOf(EnumPlayerPart.class);
                for (EnumPlayerPart part : hopefullyAllParts) {
                    neighboursOfNeighbours.addAll((Collection<EnumPlayerPart>)part.getNeighbours());
                }
                hopefullyAllParts.addAll(neighboursOfNeighbours);
            }
            if (hopefullyAllParts.size() == VALUES.length) continue;
            throw new RuntimeException(String.valueOf((Object)value) + " could not read all player parts " + Arrays.toString((Object[])hopefullyAllParts.toArray(new EnumPlayerPart[0])));
        }
    }
}

