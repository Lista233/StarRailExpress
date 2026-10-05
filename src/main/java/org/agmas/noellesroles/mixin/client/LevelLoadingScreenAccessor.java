package org.agmas.noellesroles.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * LevelLoadingScreen 的 Mixin Accessor
 * 替代 Access Widener 以避免服务端加载客户端类
 */
@Environment(EnvType.CLIENT)
@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {

    @Accessor("progressListener")
    StoringChunkProgressListener getProgressListener();
}
