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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.mixin.client.PostChainAccessor;
import ichttt.mods.firstaid.mixin.client.PostPassAccessor;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Writes float uniforms onto classic PostChain passes via Mixin accessors.
 * (Uniform class package differs across mappings -?always use reflection for set.)
 */
public final class PostChainUniforms {
    private static boolean loggedOk;
    private static boolean loggedFail;

    private PostChainUniforms() {
    }

    /**
     * @return number of passes that accepted the uniform
     */
    public static int setFloat(PostChain chain, String uniformName, float value) {
        if (chain == null) {
            return 0;
        }

        int applied = 0;

        // 1.20.1: no public PostChain#setUniform

        try {
            List<PostPass> passes = ((PostChainAccessor) (Object) chain).firstaid$getPasses();
            if (passes != null) {
                for (PostPass pass : passes) {
                    if (pass == null) {
                        continue;
                    }
                    EffectInstance effect = ((PostPassAccessor) (Object) pass).firstaid$getEffect();
                    if (effect != null && writeUniform(effect, uniformName, value)) {
                        applied++;
                    }
                }
            }
        } catch (Throwable t) {
            if (!loggedFail) {
                loggedFail = true;
                FirstAid.LOGGER.warn("PostChain accessor uniform path failed for {}", uniformName, t);
            }
        }

        if (applied > 0 && !loggedOk) {
            loggedOk = true;
            FirstAid.LOGGER.info("PostChain uniforms active ({} pass(es), {}={})", applied, uniformName, value);
        }
        return applied;
    }

    private static boolean writeUniform(EffectInstance effect, String name, float value) {
        try {
            try {
                Method safe = effect.getClass().getMethod("safeGetUniform", String.class);
                Object opt = safe.invoke(effect, name);
                if (opt instanceof Optional<?> optional) {
                    return optional.isPresent() && setUniformObject(optional.get(), value);
                }
            } catch (NoSuchMethodException ignored) {
            }

            Object uniform = effect.getUniform(name);
            return uniform != null && setUniformObject(uniform, value);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean setUniformObject(Object uniform, float value) {
        try {
            Method set = uniform.getClass().getMethod("set", float.class);
            set.invoke(uniform, value);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Method setArr = uniform.getClass().getMethod("set", float[].class);
            setArr.invoke(uniform, (Object) new float[]{value});
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
