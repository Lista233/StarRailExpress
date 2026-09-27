package org.agmas.noellesroles.game.roles.killer.nature_spirit;

import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.game.DiscountShopEntry;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 自然精灵：杀手阵营。技能键伪装成脚下的方块并对齐到该方格。
 * MorphApi 只能换玩家皮肤，方块外形走 {@link EntityDisguise}（falling_block）。
 */
public class NatureSpiritRole extends io.wifi.starrailexpress.api.NormalRole {

    public static final int SKILL_COOLDOWN_SECONDS = 60;
    public static final int BAMBOO_SPEAR_PRICE = 130;
    public static final int BAMBOO_PRICE = 50;
    public static final int AREA_BLACKOUT_PRICE = 120;
    public static final int LOCKPICK_PRICE = 60;

    public NatureSpiritRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new DiscountShopEntry(ModItems.BAMBOO_SPEAR.getDefaultInstance(), BAMBOO_SPEAR_PRICE));
        shop.add(new ShopEntry(ModItems.BAMBOO.getDefaultInstance(), BAMBOO_PRICE, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.AREA_BLACKOUT.getDefaultInstance(), AREA_BLACKOUT_PRICE, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                if (player.getCooldowns().isOnCooldown(ModItems.AREA_BLACKOUT)) {
                    return false;
                }
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(player.level());
                if (blackout.isBlackoutActive()) {
                    return false;
                }
                blackout.triggerBlackout(player.blockPosition(),
                        NoellesRolesConfig.HANDLER.instance().dreamBlackoutRadius, true,
                        SREWorldBlackoutComponent.getMaxDuration(player.level()));
                player.getCooldowns().addCooldown(ModItems.AREA_BLACKOUT,
                        Math.max(60 * 20, GameConstants.getBlackoutCooldownGlobal()));
                return true;
            }
        });
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), LOCKPICK_PRICE, ShopEntry.Type.TOOL));
        return shop;
    }

    public static boolean triggerSkill(ServerPlayer player) {
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        if (EntityDisguise.isDisguised(player)) {
            // 解除伪装：返回 true 让技能框架计入冷却（伪装期间不进入冷却）
            EntityDisguise.clear(player);
            player.displayClientMessage(Component.translatable("message.noellesroles.nature_spirit.camouflage.end")
                    .withStyle(ChatFormatting.GREEN), true);
            return true;
        }
        // 进入伪装：返回 false，不计入冷却；冷却在解除伪装时才开始
        disguiseAsFloorBlock(player);
        return false;
    }

    private static boolean disguiseAsFloorBlock(ServerPlayer player) {
        BlockPos floor = player.getOnPos();
        BlockState state = player.level().getBlockState(floor);
        if (!canCamouflageAs(state)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.nature_spirit.camouflage.fail")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        CompoundTag nbt = new CompoundTag();
        nbt.put("BlockState", NbtUtils.writeBlockState(state));
        // 保持玩家自己的眼高：地毯、压力板这类薄方块的碰撞箱只有几像素高，
        // 跟着实体眼高压下去相机会掉进地板，玩家自己就什么都看不见了。
        if (!EntityDisguise.disguiseKeepEyeHeight(player, EntityType.FALLING_BLOCK, nbt, 0)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.nature_spirit.camouflage.fail")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // 只把水平位置对齐到方格中心，<b>高度保持不变</b>：按方块碰撞箱顶面下压会让
        // 薄方块（地毯）下的自然精灵整个陷进地板里。竖直方向的观感由渲染端负责
        // （按 shift 时对齐到整数方格，见 EntityDisguiseRenderer）。
        double x = floor.getX() + 0.5;
        double y = player.getY();
        double z = floor.getZ() + 0.5;
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    x, y + 0.4, z, 16, 0.25, 0.25, 0.25, 0.05);
            serverLevel.playSound(null, x, y, z, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 0.9f, 1.15f);
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.nature_spirit.camouflage.start")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    /**
     * 能不能扮成这个方块。
     * <p>
     * 只收<b>普通方块模型</b>：
     * <ul>
     * <li>{@link RenderShape#INVISIBLE}（空气、屏障、水……）会让自然精灵彻底隐形，是白给的无敌；</li>
     * <li>{@link RenderShape#ENTITYBLOCK_ANIMATED}（箱子、告示牌、床、旗帜、头颅……）的外形来自
     * 方块实体渲染器，falling_block 只画得出静态方块模型，扮出来是个空壳；</li>
     * <li><b>头颅</b>额外显式拒绝：头颅在 1.21 里是 ENTITYBLOCK_ANIMATED，上面一条已经拦住了，
     * 但这是需求明确点名的限制，写死一条免得以后原版把它改成普通模型时悄悄放行。</li>
     * </ul>
     */
    private static boolean canCamouflageAs(BlockState state) {
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return false;
        }
        return !(state.getBlock() instanceof AbstractSkullBlock);
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        if (victim instanceof ServerPlayer serverPlayer) {
            EntityDisguise.clear(serverPlayer);
        }
        super.onDeath(victim, spawnBody, killer, deathReason, forceDeath);
    }
}
