package org.agmas.noellesroles.role.qust.roles.american_police;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.morph.MorphApi;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 美国警察玩家组件
 * <ul>
 *   <li>跟踪已完成任务数，每7个任务奖励一次技能使用次数</li>
 *   <li>维护被标记玩家列表（用于皮肤变更和小脑豁免）</li>
 *   <li>通过 CCA 同步标记状态到客户端</li>
 * </ul>
 */
public class AmericanPolicePlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** black_man 皮肤纹理路径（slim/Alex 模型） */
    public static final ResourceLocation BLACK_MAN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/entity/player/black_man.png");

    private final Player player;

    public AmericanPolicePlayerComponent(Player player) {
        this.player = player;
    }

    private int completedTaskCount = 0;
    private int tasksForNextCharge = 7;
    /**
     * 当前玩家是否被美国警察标记。
     * <p>标记状态存在被标记者自己的组件上（而非警察的组件），
     * 因为客户端皮肤处理 {@code AmericanPoliceClientState} 与小脑豁免
     * {@code XiaoNaoHandler} 都读取被标记者自身的组件。
     */
    private boolean marked = false;

    @Override
    public void init() {
        completedTaskCount = 0;
        tasksForNextCharge = QUSTConfig.instance().americanPoliceTasksPerCharge;
        marked = false;
    }

    public int getCompletedTaskCount() { return completedTaskCount; }

    public int getTasksPerCharge() { return tasksForNextCharge; }

    /** 是否被标记（读取自身组件） */
    public boolean isMarked() {
        return marked;
    }

    /** 设置自身的被标记状态并同步到客户端 */
    public void setMarked(boolean value) {
        this.marked = value;
        sync();
    }

    public void callOnFinishQuest(Player player, String quest) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        completedTaskCount++;
        SRE.LOGGER.info("[AmericanPolice] 完成任务: " + quest + ", 当前: " + completedTaskCount);

        // 每完成 N 个任务，奖励一次技能使用次数
        if (completedTaskCount % tasksForNextCharge == 0) {
            addBonusCharge(serverPlayer);
        }

        sync();
    }

    /**
     * 技能使用：标记目标玩家
     */
    public boolean useSkill(Player target) {
        if (!(target instanceof ServerPlayer serverTarget))
            return false;
        if (!(player instanceof ServerPlayer serverPlayer))
            return false;

        // 在目标自身的组件上记录”被标记”状态并同步：
        // 客户端皮肤处理与小脑豁免都读取被标记者自己的组件，因此标记必须写在目标身上。
        var targetComp = QUSTComponentKeys.Keys.AMERICAN_POLICE.maybeGet(serverTarget).orElse(null);
        if (targetComp != null) {
            targetComp.setMarked(true);
        }

        // 使用 MorphApi 改变目标玩家皮肤（服务端权威变形）
        MorphApi.morphToTexture(serverTarget, BLACK_MAN_TEXTURE, true);

        // 发送提示
        serverPlayer.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.american_police.marked", target.getName()),
                true);
        serverTarget.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.american_police.you_marked"),
                true);

        return true;
    }

    /**
     * 击杀奖励：如果击杀的是杀手或中立角色，增加一次技能使用次数
     */
    public void onKillPlayer(Player victim) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (!(victim instanceof ServerPlayer serverVictim))
            return;

        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameComponent == null) return;

        var victimRole = gameComponent.getRole(serverVictim);
        if (victimRole == null) return;

        // 如果受害者是杀手或中立角色，奖励一次技能使用次数
        if (!victimRole.isInnocent()) {
            addBonusCharge(serverPlayer);
            serverPlayer.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.american_police.bonus_charge"),
                    true);
        }
    }

    private void addBonusCharge(ServerPlayer serverPlayer) {
        var ability = SREAbilityPlayerComponent.KEY.get(serverPlayer);
        var definitions = RoleSkill.getDefinitions(
                SREGameWorldComponent.KEY.get(serverPlayer.level()).getRole(serverPlayer));
        for (var def : definitions) {
            if (QUSTRoles.AMERICAN_POLICE_SKILL_ID.equals(def.id())) {
                ability.addSkillCharges(def, 1);
                SRE.LOGGER.info("[AmericanPolice] 奖励技能使用次数");
                break;
            }
        }
    }

    private void sync() {
        if (!player.level().isClientSide) {
            QUSTComponentKeys.Keys.AMERICAN_POLICE.sync(player);
        }
    }

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) { return true; }

    @Override
    public void clear() { init(); }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("CompletedTaskCount", this.completedTaskCount);
        tag.putBoolean("Marked", this.marked);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.completedTaskCount = tag.getInt("CompletedTaskCount");
        this.marked = tag.getBoolean("Marked");
    }

    @Override
    public void clientTick() {}

    @Override
    public void serverTick() {}

    @Override
    public void readFromNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {}

    @Override
    public void writeToNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {}
}
