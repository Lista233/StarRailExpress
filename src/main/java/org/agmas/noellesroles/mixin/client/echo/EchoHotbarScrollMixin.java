package org.agmas.noellesroles.mixin.client.echo;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.agmas.noellesroles.game.modes.echo.client.EchoHotbar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 滚轮只在 0..2 之间移动，不回绕到空槽位。 */
@Mixin(Inventory.class)
public abstract class EchoHotbarScrollMixin {
    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    private void noellesroles$echoSwapPaint(double direction, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.player.getInventory() != (Inventory) (Object) this) return;
        if (!EchoHotbar.active()) return;
        Inventory inventory = (Inventory) (Object) this;
        inventory.selected = Mth.clamp(inventory.selected - (int) Math.signum(direction), 0, 2);
        ci.cancel();
    }
}
