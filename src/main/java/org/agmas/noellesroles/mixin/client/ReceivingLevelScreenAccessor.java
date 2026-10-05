package org.agmas.noellesroles.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.BooleanSupplier;

/**
 * ReceivingLevelScreen 的 Mixin Accessor
 * 替代 Access Widener 以避免服务端加载客户端类
 */
@Environment(EnvType.CLIENT)
@Mixin(ReceivingLevelScreen.class)
public interface ReceivingLevelScreenAccessor {

    @Accessor("levelReceived")
    BooleanSupplier getLevelReceived();

    @Accessor("reason")
    ReceivingLevelScreen.Reason getReason();
}
