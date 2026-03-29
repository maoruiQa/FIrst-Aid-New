/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nullable
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.storage.ValueInput
 *  net.minecraft.world.level.storage.ValueOutput
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.FirstAidDamageModelHolder;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Player.class})
public abstract class PlayerEntityMixin
implements FirstAidDamageModelHolder {
    private static final String FIRSTAID_NBT_KEY = "FirstAidDamageModel";
    @Unique
    private PlayerDamageModel firstaid$damageModel;

    @Override
    public PlayerDamageModel firstaid$getDamageModel() {
        if (this.firstaid$damageModel == null) {
            this.firstaid$damageModel = new PlayerDamageModel();
        }
        return this.firstaid$damageModel;
    }

    @Override
    @Nullable
    public PlayerDamageModel firstaid$getDamageModelNullable() {
        return this.firstaid$damageModel;
    }

    @Override
    public void firstaid$setDamageModel(PlayerDamageModel model) {
        this.firstaid$damageModel = model;
    }

    @Inject(method={"readAdditionalSaveData"}, at={@At(value="TAIL")})
    private void firstaid$readAdditionalSaveData(ValueInput input, CallbackInfo ci) {
        input.read(FIRSTAID_NBT_KEY, CompoundTag.CODEC).ifPresent(tag -> this.firstaid$getDamageModel().deserializeNBT((CompoundTag)tag));
    }

    @Inject(method={"addAdditionalSaveData"}, at={@At(value="TAIL")})
    private void firstaid$addAdditionalSaveData(ValueOutput output, CallbackInfo ci) {
        PlayerDamageModel model = this.firstaid$getDamageModelNullable();
        if (model != null) {
            output.store(FIRSTAID_NBT_KEY, CompoundTag.CODEC, (Object)model.serializeNBT());
        }
    }
}

