package org.agmas.noellesroles.mixin.client.roles.command_block;

import org.agmas.noellesroles.client.CommandBlockIntroClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.exmo.sre.camera.AdvancedCameraSequence;
import net.exmo.sre.camera.client.AdvancedCameraDirector;

/** 命令方块镜头期间，后到的地图进场轨道不能再把镜头抢走。 */
@Mixin(AdvancedCameraDirector.class)
public class CommandBlockCameraMixin {
    @Inject(method = "startEntityFocus", at = @At("TAIL"))
    private static void watchCommandBlock(int entityId, int durationTicks, boolean blackBars, CallbackInfo ci) {
        CommandBlockIntroClient.watch(entityId, durationTicks);
    }

    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private static void keepCommandBlockCamera(AdvancedCameraSequence sequence, CallbackInfo ci) {
        if (CommandBlockIntroClient.isActive()) {
            ci.cancel();
        }
    }
}
