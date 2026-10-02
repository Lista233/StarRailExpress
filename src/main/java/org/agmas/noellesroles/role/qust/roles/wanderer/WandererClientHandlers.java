package org.agmas.noellesroles.role.qust.roles.wanderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.exmo.sre.camera.client.AdvancedCameraDirector;

/**
 * 游荡者客户端处理：灵魂出窍自由相机
 */
public class WandererClientHandlers {

    private static boolean freeCamActive = false;
    private static Vec3 camPos = Vec3.ZERO;

    public static boolean isFreeCamActive() { return freeCamActive; }

    /** 服务端通知进入灵魂出窍 */
    public static void enterSoulOut() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;
        freeCamActive = true;
        camPos = player.getEyePosition();
    }

    /** 服务端通知退出灵魂出窍 */
    public static void exitSoulOut() {
        freeCamActive = false;
        AdvancedCameraDirector.clearFixedOverride();
    }

    /** 客户端每 tick 调用（注册于 QUSTClient） */
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (!freeCamActive) return;
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            freeCamActive = false;
            AdvancedCameraDirector.clearFixedOverride();
            return;
        }

        // 界面打开 = 取消
        if (client.screen != null) {
            exitSoulOut();
            return;
        }

        // 移动相机
        moveCamera(client, player);

        // 相机距本体限制
        Vec3 origin = player.getEyePosition();
        Vec3 offset = camPos.subtract(origin);
        double maxDist = 28.0;
        if (offset.length() > maxDist) {
            camPos = origin.add(offset.normalize().scale(maxDist));
        }

        AdvancedCameraDirector.setFixedOverride(camPos, player.getYRot(),
                Mth.clamp(player.getXRot(), -89.0F, 89.0F), 0.0F);
    }

    private static void moveCamera(Minecraft client, LocalPlayer player) {
        double speed = 0.6;
        if (player.isSprinting()) speed *= 2.0;

        Vec3 forward = player.getLookAngle().scale(speed);
        Vec3 right = player.getLookAngle().yRot((float) (-Math.PI / 2)).scale(speed);

        // WASD 移动
        if (client.options.keyUp.isDown()) camPos = camPos.add(forward);
        if (client.options.keyDown.isDown()) camPos = camPos.subtract(forward);
        if (client.options.keyLeft.isDown()) camPos = camPos.subtract(right);
        if (client.options.keyRight.isDown()) camPos = camPos.add(right);
        if (client.options.keyJump.isDown()) camPos = camPos.add(0, speed, 0);
        if (client.options.keyShift.isDown()) camPos = camPos.add(0, -speed, 0);
    }
}
