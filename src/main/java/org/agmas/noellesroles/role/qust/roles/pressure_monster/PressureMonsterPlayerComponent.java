package org.agmas.noellesroles.role.qust.roles.pressure_monster;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 压力怪玩家组件
 * <ul>
 *   <li>管理技能冷却减少（击杀 -30s，小脑事件 -90s）</li>
 *   <li>通过 CCA 同步状态到客户端</li>
 * </ul>
 */
public class PressureMonsterPlayerComponent implements RoleComponent, ServerTickingComponent {

    private final Player player;

    public PressureMonsterPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void init() {
    }

    /**
     * 击杀奖励：减少技能冷却 30 秒
     */
    public void onKillPlayer() {
        if (!(player instanceof ServerPlayer sp)) return;

        int reductionTicks = QUSTConfig.instance().pressureMonsterKillCdReductionSeconds * 20;
        reduceSkillCooldown(sp, reductionTicks);

        sp.displayClientMessage(
                Component.translatable("message.pressure_monster.kill_cd_reduce",
                        QUSTConfig.instance().pressureMonsterKillCdReductionSeconds)
                        .withStyle(ChatFormatting.RED),
                true);
    }

    /**
     * 小脑事件奖励：减少技能冷却 90 秒
     */
    public void onXiaonaoTriggered() {
        if (!(player instanceof ServerPlayer sp)) return;

        int reductionTicks = QUSTConfig.instance().pressureMonsterXiaonaoCdReductionSeconds * 20;
        reduceSkillCooldown(sp, reductionTicks);

        sp.displayClientMessage(
                Component.translatable("message.pressure_monster.xiaonao_cd_reduce",
                        QUSTConfig.instance().pressureMonsterXiaonaoCdReductionSeconds)
                        .withStyle(ChatFormatting.DARK_PURPLE),
                true);
    }

    private void reduceSkillCooldown(ServerPlayer sp, int reductionTicks) {
        var ability = SREAbilityPlayerComponent.KEY.get(sp);
        var state = ability.getSkillState(QUSTRoles.PRESSURE_MONSTER_SKILL_ID);
        if (state.cooldown > 0) {
            int newCd = Math.max(0, state.cooldown - reductionTicks);
            ability.setSkillCooldown(QUSTRoles.PRESSURE_MONSTER_SKILL_ID, newCd);
            SRE.LOGGER.info("[PressureMonster] CD reduced by {}s, remaining: {}s",
                    reductionTicks / 20, newCd / 20);
        }
    }

    /**
     * 获取当前技能冷却秒数（客户端读取）
     */
    public int getCooldownSeconds() {
        if (player.level().isClientSide) {
            var ability = SREAbilityPlayerComponent.KEY.get(player);
            var state = ability.getSkillState(QUSTRoles.PRESSURE_MONSTER_SKILL_ID);
            return (state.cooldown + 19) / 20;
        }
        return 0;
    }

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) { return true; }

    @Override
    public void clear() {}

    @Override
    public void serverTick() {}

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {}

    @Override
    public void writeToNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {}
}
