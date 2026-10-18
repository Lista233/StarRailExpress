package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.agmas.noellesroles.game.command_block.DreadlordRole;
import org.agmas.noellesroles.role_data.killer.DreadlordRoleData;

/**
 * 恐惧魔王能看见旁观者的头。黑暗游走时本体改成贴地的一团黑。
 */
@Environment(EnvType.CLIENT)
public final class DreadlordClientFx {
    private DreadlordClientFx() {
    }

    public static boolean renderDark(AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffer,
            int light) {
        DreadlordRoleData data = DreadlordRole.dataOf(player);
        if (data == null || !data.isDark()) {
            return false;
        }
        var consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(
                ResourceLocation.withDefaultNamespace("textures/misc/shadow.png")));
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.05F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(1.6F, 1.6F, 1.6F);
        var pose = poseStack.last();
        consumer.addVertex(pose, -0.5F, -0.5F, 0.0F).setColor(20, 0, 30, 200).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, -0.5F, 0.5F, 0.0F).setColor(20, 0, 30, 200).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, 0.5F, 0.5F, 0.0F).setColor(20, 0, 30, 200).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, 0.5F, -0.5F, 0.0F).setColor(20, 0, 30, 200).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        poseStack.popPose();
        return true;
    }

    public static void renderSouls(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !DreadlordRole.isDreadlord(client.player)) {
            return;
        }
        var camera = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        if (poseStack == null) {
            return;
        }
        MultiBufferSource buffer = client.renderBuffers().bufferSource();
        for (Player other : client.level.players()) {
            if (!other.isSpectator() || other == client.player) {
                continue;
            }
            if (!(other instanceof AbstractClientPlayer soul)) {
                continue;
            }
            double x = Mth.lerp(context.tickCounter().getGameTimeDeltaPartialTick(false), soul.xo, soul.getX()) - camera.x;
            double y = Mth.lerp(context.tickCounter().getGameTimeDeltaPartialTick(false), soul.yo, soul.getY()) - camera.y;
            double z = Mth.lerp(context.tickCounter().getGameTimeDeltaPartialTick(false), soul.zo, soul.getZ()) - camera.z;
            poseStack.pushPose();
            poseStack.translate(x, y + 1.6D, z);
            poseStack.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(0.35F, 0.35F, 0.35F);
            ResourceLocation skin = soul.getSkin().texture();
            var consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(skin));
            var pose = poseStack.last();
            float u0 = 8.0F / 64.0F;
            float v0 = 8.0F / 64.0F;
            float u1 = 16.0F / 64.0F;
            float v1 = 16.0F / 64.0F;
            consumer.addVertex(pose, -0.5F, 0.5F, 0.0F).setColor(180, 180, 255, 220).setUv(u0, v0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 0.0F, 1.0F);
            consumer.addVertex(pose, -0.5F, -0.5F, 0.0F).setColor(180, 180, 255, 220).setUv(u0, v1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 0.0F, 1.0F);
            consumer.addVertex(pose, 0.5F, -0.5F, 0.0F).setColor(180, 180, 255, 220).setUv(u1, v1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 0.0F, 1.0F);
            consumer.addVertex(pose, 0.5F, 0.5F, 0.0F).setColor(180, 180, 255, 220).setUv(u1, v0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 0.0F, 1.0F);
            poseStack.popPose();
        }
    }

    private static final List<FloatBlock> FLOATS = new ArrayList<>();
    private static int nextLift;

    public static boolean hides(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        for (FloatBlock block : FLOATS) {
            if (block.key == key) {
                return true;
            }
        }
        return false;
    }

    public static void tickFloats(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (!FLOATS.isEmpty()) {
                markAll(client);
                FLOATS.clear();
            }
            return;
        }
        boolean frenzy = false;
        for (Player other : client.level.players()) {
            DreadlordRoleData data = DreadlordRole.dataOf(other);
            if (data != null && data.isKilling() && !data.isDark()) {
                frenzy = true;
                if (client.player.tickCount >= nextLift) {
                    liftAround(client, other);
                }
            }
        }
        if (frenzy && client.player.tickCount >= nextLift) {
            nextLift = client.player.tickCount + 12;
        }
        Iterator<FloatBlock> it = FLOATS.iterator();
        while (it.hasNext()) {
            FloatBlock block = it.next();
            block.age++;
            if (block.age >= 50) {
                client.levelRenderer.setSectionDirty(block.pos.getX(), block.pos.getY(), block.pos.getZ());
                it.remove();
            }
        }
    }

    private static void liftAround(Minecraft client, Player source) {
        BlockPos origin = source.blockPosition();
        for (int i = 0; i < 4; i++) {
            int dx = client.player.getRandom().nextInt(7) - 3;
            int dz = client.player.getRandom().nextInt(7) - 3;
            BlockPos pos = origin.offset(dx, -1, dz);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos at = pos.offset(0, dy, 0);
                BlockState state = client.level.getBlockState(at);
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    continue;
                }
                boolean exists = false;
                for (FloatBlock block : FLOATS) {
                    if (block.pos.equals(at)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    FLOATS.add(new FloatBlock(at, state));
                    client.levelRenderer.setSectionDirty(at.getX(), at.getY(), at.getZ());
                }
                break;
            }
        }
    }

    public static void renderFloats(WorldRenderContext context) {
        if (FLOATS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource buffer = context.consumers();
        if (poseStack == null || buffer == null) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        var dispatcher = client.getBlockRenderer();
        for (FloatBlock block : FLOATS) {
            float t = Mth.clamp((block.age + partial) / 50.0F, 0.0F, 1.0F);
            float rise = (float) Math.sin(t * Math.PI) * 1.15F;
            poseStack.pushPose();
            poseStack.translate(block.pos.getX() - camera.x, block.pos.getY() - camera.y + rise,
                    block.pos.getZ() - camera.z);
            dispatcher.renderSingleBlock(block.state, poseStack, buffer, 0xF000F0, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    private static void markAll(Minecraft client) {
        if (client.levelRenderer == null) {
            return;
        }
        for (FloatBlock block : FLOATS) {
            client.levelRenderer.setSectionDirty(block.pos.getX(), block.pos.getY(), block.pos.getZ());
        }
    }

    private static final class FloatBlock {
        private final BlockPos pos;
        private final long key;
        private final BlockState state;
        private int age;

        private FloatBlock(BlockPos pos, BlockState state) {
            this.pos = pos.immutable();
            this.key = this.pos.asLong();
            this.state = state;
        }
    }
}
