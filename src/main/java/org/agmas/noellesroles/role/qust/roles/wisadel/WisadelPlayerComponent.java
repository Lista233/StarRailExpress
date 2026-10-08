package org.agmas.noellesroles.role.qust.roles.wisadel;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.jetbrains.annotations.NotNull;

/**
 * 维什戴尔_星魂（Wisadel）玩家组件。
 * <ul>
 *   <li>魂灵（souls）：右键与玩家尸体交互汲取 +1，最多存储 {@link #maxSouls()} 层；跨局复位。</li>
 *   <li>祖宗发射器购买冷却：记录下一次可购买的绝对游戏刻，独立于物品开火冷却。</li>
 * </ul>
 */
public class WisadelPlayerComponent implements RoleComponent {

    /** 魂灵上限（可配置：{@code config/qust_roles.json} 的 wisadelMaxSouls）。 */
    public static int maxSouls() {
        return Math.max(1, QUSTConfig.instance().wisadelMaxSouls);
    }

    /** 购买一次祖宗发射器消耗的魂灵层数（可配置：wisadelShotgunSoulCost）。 */
    public static int shotgunSoulCost() {
        return Math.max(1, QUSTConfig.instance().wisadelShotgunSoulCost);
    }

    /** 祖宗发射器购买冷却（ticks，可配置：wisadelShotgunPurchaseCooldownSeconds 秒）。 */
    public static int shotgunPurchaseCooldownTicks() {
        return Math.max(0, QUSTConfig.instance().wisadelShotgunPurchaseCooldownSeconds) * 20;
    }

    private final Player player;

    /** 当前存储的魂灵层数（0 ~ maxSouls()） */
    private int souls = 0;

    /** 祖宗发射器下一次可购买的绝对游戏刻（<= 当前游戏刻表示可购买） */
    private long shotgunNextPurchaseTick = 0L;

    public WisadelPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        souls = 0;
        shotgunNextPurchaseTick = 0L;
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) {
        return spectator.getUUID().equals(player.getUUID());
    }

    private void sync() {
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            QUSTComponentKeys.Keys.WISADEL.sync(player);
        }
    }

    // ==================== 魂灵 ====================

    public int getSouls() {
        return souls;
    }

    /** 汲取一具尸体的魂灵：+1（封顶 {@link #maxSouls()}）。 */
    public void addSoul() {
        if (souls < maxSouls()) {
            souls++;
            sync();
        }
    }

    /** 是否满足购买祖宗发射器所需的魂灵层数。 */
    public boolean hasEnoughSouls() {
        return souls >= shotgunSoulCost();
    }

    /** 购买时消耗 {@link #shotgunSoulCost()} 层魂灵。 */
    public boolean consumeSoulsForShotgun() {
        if (souls >= shotgunSoulCost()) {
            souls -= shotgunSoulCost();
            sync();
            return true;
        }
        return false;
    }

    // ==================== 发射器购买冷却 ====================

    /** 当前游戏刻是否已过购买冷却（可购买）。 */
    public boolean isShotgunPurchaseReady(long currentGameTime) {
        return currentGameTime >= shotgunNextPurchaseTick;
    }

    /** 记录一次购买：设置下一次可购买的游戏刻。 */
    public void markShotgunPurchased(long currentGameTime) {
        this.shotgunNextPurchaseTick = currentGameTime + shotgunPurchaseCooldownTicks();
        sync();
    }

    // ==================== NBT ====================

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Souls", souls);
        tag.putLong("ShotgunNextPurchaseTick", shotgunNextPurchaseTick);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        souls = tag.getInt("Souls");
        shotgunNextPurchaseTick = tag.getLong("ShotgunNextPurchaseTick");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }
}
