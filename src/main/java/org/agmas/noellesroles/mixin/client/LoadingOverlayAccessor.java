package org.agmas.noellesroles.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * LoadingOverlay 的 Mixin Accessor
 * 替代 Access Widener 以避免服务端加载客户端类
 */
@Environment(EnvType.CLIENT)
@Mixin(LoadingOverlay.class)
public interface LoadingOverlayAccessor {

    @Accessor("minecraft")
    Minecraft getMinecraft();

    @Accessor("reload")
    ReloadInstance getReload();

    @Accessor("onFinish")
    Consumer<Optional<Throwable>> getOnFinish();

    @Accessor("fadeIn")
    boolean isFadeIn();
}
