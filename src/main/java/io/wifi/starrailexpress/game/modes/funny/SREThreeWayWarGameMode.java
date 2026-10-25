/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.util.TickTimer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.CustomWinnerClass;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role.touhou.THHumanVillageRoles;
import org.agmas.noellesroles.role.touhou.THMountainRoles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 三方会战 (haiman:three_way_war)
 *
 * <p>最低 12 人开局，时长 10 分钟。场上不分配任何修饰符，所有玩家（林家子弟除外）开局携带
 * {@value #STARTING_GOLD} 金币。
 *
 * <p>职业分配：把当前人数平均分为四组——教父组、蜜蜂组、兔兔组、辅助组；人数无法被 4 整除时，
 * 余出的人数全部并入辅助组。人数不足时依旧严格按照下列顺序分配：
 * <ul>
 * <li>教父组：教父 → 家族侍卫 → 家族教徒 → 家族保姆 → 家族保护伞（循环）</li>
 * <li>蜜蜂组：蜂后 → 马蜂；组内大于等于 5 人时再生成一个蜂后，其余为马蜂</li>
 * <li>兔兔组：全员兔兔万岁</li>
 * <li>辅助组：林家子弟（仅 1 个）→ 领袖（最多 3 个）→ 荷官商人 → 香霖堂主 → 小偷 → 鹈鹕
 * （后五者循环）</li>
 * </ul>
 *
 * <p>结算：教父组、蜜蜂组、兔兔组是三方会战的三方，彼此对立，各组沿用其职业自身的独立胜利条件，
 * 任一方达成胜利条件时即按该方单独结算。辅助组不属于三方中的任何一方，是支援方，不以击败其他组
 * 为目标，只按自身职业的判定结算（如林家子弟花光开局资金即获胜）。
 *
 * <p>本模式专属效果（不作用于其它模式）：
 * <ul>
 * <li>兔兔万岁的技能冷却固定为 {@value #RABBIT_SKILL_COOLDOWN_SECONDS} 秒</li>
 * <li>所有存活玩家每 30 秒发光 8 秒</li>
 * <li>蜂后复活出的工蜂资金减少 70%；开局生成的工蜂初始资金减少 50%</li>
 * </ul>
 */
public class SREThreeWayWarGameMode extends SREBaseCustomizationGameMode {

    /** 模式时长（分钟） */
    private static final int DEFAULT_TIME_IN_MINUTES = 10;

    /** 模式最低开局人数 */
    private static final int MIN_PLAYER_COUNT = 12;

    /** 所有玩家的开局金币（林家子弟不受此影响） */
    public static final int STARTING_GOLD = 3000;

    /** 蜜蜂组人数达到该值时，额外生成第二个蜂后 */
    private static final int BEE_SECOND_QUEEN_THRESHOLD = 5;

    /** 辅助组中领袖职业的生成上限 */
    private static final int SUPPORT_LEADER_LIMIT = 3;

    /** 本模式下兔兔万岁的技能冷却（秒） */
    public static final int RABBIT_SKILL_COOLDOWN_SECONDS = 5;

    /** 本模式下兔兔万岁的技能冷却（tick） */
    private static final int RABBIT_SKILL_COOLDOWN_TICKS = RABBIT_SKILL_COOLDOWN_SECONDS * 20;

    /** 本模式下所有存活玩家的发光间隔（tick，30 秒） */
    private static final int GLOW_INTERVAL_TICKS = 30 * 20;

    /** 本模式下所有存活玩家每次发光的持续时长（tick，8 秒） */
    private static final int GLOW_DURATION_TICKS = 8 * 20;

    /** 本模式下蜂后复活出的工蜂保留的资金比例（减少 70%） */
    public static final float BEE_WORKER_REVIVE_MONEY_KEEP = 0.3F;

    /** 本模式下开局生成的工蜂保留的初始资金比例（减少 50%） */
    public static final float BEE_WORKER_STARTING_MONEY_KEEP = 0.5F;

    /** 教父组循环分配顺序：教父 → 家族侍卫 → 家族教徒 → 家族保姆 → 家族保护伞 */
    private static final SRERole[] GODFATHER_SQUAD_CYCLE = {
            ModRoles.GODFATHER,
            ModRoles.JANITOR,
            ModRoles.MAFIOSO,
            ModRoles.NUTRITIONIST,
            ModRoles.PARASOL
    };

    /** 辅助组循环分配顺序：领袖 → 荷官商人 → 香霖堂主 → 小偷 → 鹈鹕（林家子弟固定占第一位） */
    private static final SRERole[] SUPPORT_SQUAD_CYCLE = {
            ModRoles.LEADER,
            THMountainRoles.NITORI,
            THHumanVillageRoles.RINNOSUKE,
            ModRoles.THIEF,
            ModRoles.PELICAN
    };

    /** 本局玩家所属分组 */
    private final Map<UUID, Squad> squadOfPlayer = new HashMap<>();

    public SREThreeWayWarGameMode(ResourceLocation identifier) {
        super(identifier, DEFAULT_TIME_IN_MINUTES, MIN_PLAYER_COUNT);
    }

    /** 当前对局是否为本模式：供本模式专属效果（如工蜂资金削减）跨模块判定使用 */
    public static boolean isActive(Level level) {
        if (level == null) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(level).getGameMode() instanceof SREThreeWayWarGameMode;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        SRETrainWorldComponent.KEY.get(serverWorld).setTimeOfDay(SRETrainWorldComponent.TimeOfDay.SUNDOWN);
        Harpymodloader.refreshRoles();

        assignRoles(serverWorld, gameWorldComponent, players);
        sendWelcomePackets(players, gameWorldComponent);
        notifySquad(players);

        tickTimers.clear();
        initTickTimers(serverWorld, gameWorldComponent, players);
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        squadOfPlayer.clear();
        super.finalizeGame(serverWorld, gameWorldComponent);
    }

    // ==================== 职业分配 ====================

    private void assignRoles(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        gameWorldComponent.clearRoleMap();
        // 本模式不分配修饰符：清空上一局可能残留的修饰符，确保场上修饰符为空
        WorldModifierComponent.KEY.get(serverWorld).clearAll();

        Map<ServerPlayer, SRERole> assignments = buildAssignments(serverWorld, players);
        for (ServerPlayer player : players) {
            SRERole role = assignments.get(player);
            if (role != null) {
                gameWorldComponent.addRole(player, role, false);
            }
        }
        gameWorldComponent.syncRolesNow();

        // 触发职业初始化：发放职业初始物品、执行 RoleData#init
        // （林家子弟的开局资金正是在这一步由 LinFamilyRoleData#init 按其自身规则发放）
        for (ServerPlayer player : players) {
            SRERole role = gameWorldComponent.getRole(player);
            if (role == null) {
                continue;
            }
            ProgressionDataManager.onRoleAssigned(player, role);
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }

        // 统一开局金币；林家子弟按自身规则发放，不受模式初始金币影响
        for (ServerPlayer player : players) {
            if (gameWorldComponent.isRole(player, ModRoles.LIN_FAMILY)) {
                continue;
            }
            int gold = STARTING_GOLD;
            // 本模式专属：开局生成的工蜂初始资金减少 50%
            if (gameWorldComponent.isRole(player, BounsRoles.BEE_WORKER)) {
                gold = (int) (gold * BEE_WORKER_STARTING_MONEY_KEEP);
            }
            SREPlayerShopComponent.KEY.get(player).setBalance(gold);
        }
    }

    /**
     * 把当前人数平均分为四组并按固定顺序分配职业。
     *
     * <p>余下无法被 4 整除的人数全部并入辅助组。
     */
    private Map<ServerPlayer, SRERole> buildAssignments(ServerLevel serverWorld, List<ServerPlayer> players) {
        Map<ServerPlayer, SRERole> assignments = new LinkedHashMap<>();
        squadOfPlayer.clear();
        if (players.isEmpty()) {
            return assignments;
        }

        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, new Random(serverWorld.getGameTime()));

        // 平均分组，余数全部并入辅助组
        int squadSize = players.size() / 4;
        int supportExtra = players.size() % 4;

        int cursor = 0;

        // 教父组：教父 → 家族侍卫 → 家族教徒 → 家族保姆 → 家族保护伞（循环）
        for (int i = 0; i < squadSize; i++) {
            ServerPlayer player = shuffled.get(cursor++);
            assignments.put(player, GODFATHER_SQUAD_CYCLE[i % GODFATHER_SQUAD_CYCLE.length]);
            squadOfPlayer.put(player.getUUID(), Squad.GODFATHER);
        }

        // 蜜蜂组：蜂后 → 马蜂；组内人数 >= 5 时生成两个蜂后，其余为马蜂
        for (int i = 0; i < squadSize; i++) {
            ServerPlayer player = shuffled.get(cursor++);
            boolean isQueen = squadSize >= BEE_SECOND_QUEEN_THRESHOLD ? i < 2 : i == 0;
            assignments.put(player, isQueen ? BounsRoles.BEE_QUEEN : BounsRoles.BEE_WASP);
            squadOfPlayer.put(player.getUUID(), Squad.BEE);
        }

        // 兔兔组：全员兔兔万岁
        for (int i = 0; i < squadSize; i++) {
            ServerPlayer player = shuffled.get(cursor++);
            assignments.put(player, BounsRoles.RABBIT_WANSUI);
            squadOfPlayer.put(player.getUUID(), Squad.RABBIT);
        }

        // 辅助组：首位固定林家子弟，其后按 领袖 → 荷官商人 → 香霖堂主 → 小偷 → 鹈鹕 循环，领袖最多 3 个
        int leaderCount = 0;
        int cycleCursor = 0;
        for (int i = 0; i < squadSize + supportExtra; i++) {
            ServerPlayer player = shuffled.get(cursor++);
            SRERole role;
            if (i == 0) {
                role = ModRoles.LIN_FAMILY;
            } else {
                // 跳过已达上限的职业，继续沿循环顺序取下一个可分配的职业
                SRERole picked = null;
                for (int probe = 0; probe < SUPPORT_SQUAD_CYCLE.length; probe++) {
                    SRERole candidate = SUPPORT_SQUAD_CYCLE[cycleCursor % SUPPORT_SQUAD_CYCLE.length];
                    cycleCursor++;
                    if (candidate == ModRoles.LEADER && leaderCount >= SUPPORT_LEADER_LIMIT) {
                        continue;
                    }
                    picked = candidate;
                    break;
                }
                role = picked != null ? picked : ModRoles.LEADER;
                if (role == ModRoles.LEADER) {
                    leaderCount++;
                }
            }
            assignments.put(player, role);
            squadOfPlayer.put(player.getUUID(), Squad.SUPPORT);
        }

        return assignments;
    }

    /** 广播提示每位玩家自己所属的分组（参考邪恶战争的开局广播） */
    private void notifySquad(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Squad squad = squadOfPlayer.get(player.getUUID());
            if (squad != null) {
                broadcastMessageTo(player,
                        Component.translatable(squad.langKey).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    // ==================== 主循环与结算 ====================

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
        if (gameWorldComponent.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            return;
        }

        tickPassiveMoney(serverWorld, gameWorldComponent);
        tickRabbitSkillCooldown(serverWorld, gameWorldComponent);

        boolean anyAlive = false;
        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                anyAlive = true;
                break;
            }
        }

        WinStatus winStatus = WinStatus.NONE;
        if (!anyAlive) {
            winStatus = WinStatus.NO_PLAYER;
        } else if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime()) {
            winStatus = WinStatus.TIME;
        }

        // 三方（教父组 / 蜜蜂组 / 兔兔组）各自独立判定，任一方达成胜利条件即按该方单独结算；
        // 辅助组不参与三方对立，只按自身职业的判定结算
        WinStatus modified = allowGameEnd(serverWorld, winStatus, gameWorldComponent);
        if (modified != WinStatus.NOT_MODIFY) {
            winStatus = modified;
        }

        if (winStatus != WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(new ArrayList<>(serverWorld.players()),
                    winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

    /** 被动金币：让商店经济在 10 分钟的短局内同样可运转 */
    private void tickPassiveMoney(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (SREGameTimeComponent.KEY.get(serverWorld).levelGameTimeFrozen) {
            return;
        }
        int balanceToAdd = GameConstants.getPassiveMoneyTicker().apply(serverWorld.getGameTime());
        if (balanceToAdd <= 0) {
            return;
        }
        for (ServerPlayer player : serverWorld.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player) || !gameWorldComponent.canAutoAddMoney(player)) {
                continue;
            }
            SREPlayerShopComponent.KEY.get(player).addToBalance(balanceToAdd);
        }
    }

    /**
     * 本模式专属：兔兔万岁的技能冷却固定为 {@value #RABBIT_SKILL_COOLDOWN_SECONDS} 秒。
     * 技能本身仍按注册时的冷却计时，这里每 tick 把剩余冷却钳制到上限，从而只在本模式生效。
     */
    private void tickRabbitSkillCooldown(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : serverWorld.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || !gameWorldComponent.isRole(player, BounsRoles.RABBIT_WANSUI)) {
                continue;
            }
            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
            boolean changed = false;
            for (RoleSkill.Definition definition : RoleSkill.getDefinitions(BounsRoles.RABBIT_WANSUI)) {
                if (definition.cooldownTicks() <= RABBIT_SKILL_COOLDOWN_TICKS) {
                    continue;
                }
                SREAbilityPlayerComponent.SkillState state = ability.getSkillState(definition.id());
                if (state.cooldown > RABBIT_SKILL_COOLDOWN_TICKS) {
                    state.cooldown = RABBIT_SKILL_COOLDOWN_TICKS;
                    changed = true;
                }
            }
            if (!changed) {
                continue;
            }
            if (ability.cooldown > RABBIT_SKILL_COOLDOWN_TICKS) {
                ability.cooldown = RABBIT_SKILL_COOLDOWN_TICKS;
            }
            ability.sync();
        }
    }

    /** 本模式专属定时器：所有存活玩家每 30 秒发光 8 秒 */
    @Override
    protected void initTickTimers(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        tickTimers.add(new TickTimer(GLOW_INTERVAL_TICKS, false, tickTimer -> {
            for (ServerPlayer player : serverWorld.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                    continue;
                }
                if (player.hasEffect(MobEffects.GLOWING)) {
                    player.removeEffect(MobEffects.GLOWING);
                }
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION_TICKS, 1, false,
                        false));
            }
        }));
    }

    /**
     * 与 {@link SREMurderGameMode#allowGameEnd} 保持同样的优先级：
     * 前置事件 → 内置独立胜利条件 → 后置事件。
     */
    private WinStatus allowGameEnd(ServerLevel serverWorld, WinStatus winStatus,
            SREGameWorldComponent gameWorldComponent) {
        WinStatus before = AllowGameEnd.EVENT_START.invoker().allowGameEnd(serverWorld, winStatus, false);
        if (before != null && !before.equals(WinStatus.NOT_MODIFY)) {
            return before;
        }

        // 内置独立胜利条件：教父家族、蜜蜂家族、兔兔万岁、林家子弟等
        WinStatus builtin = CustomWinnerClass.checkWinnerBuiltin(serverWorld, winStatus, false);
        if (builtin != null && !builtin.equals(WinStatus.NOT_MODIFY)) {
            return builtin;
        }

        return AllowGameEnd.EVENT_END.invoker().allowGameEnd(serverWorld, winStatus, false);
    }

    @Override
    public boolean isPlayerWinning(ServerLevel world, ServerPlayer player, SRERole playerRole,
            SREGameRoundEndComponent roundEnd, SREGameWorldComponent gameComponent) {
        // 复用谋杀模式的结算判定：其中会调用各职业的 didPlayerWin，
        // 使教父家族成员、蜜蜂家族成员随分组一同获胜
        return SREMurderGameMode.isPlayerTheWinner(world, player, playerRole, roundEnd, gameComponent);
    }

    @Override
    public boolean canHaveMeeting() {
        return false;
    }

    /** 会战参与方：三方为彼此对立的阵营，辅助组为支援方 */
    private enum Squad {
        GODFATHER("message.gamemode.three_way_war.tip.squad.godfather"),
        BEE("message.gamemode.three_way_war.tip.squad.bee"),
        RABBIT("message.gamemode.three_way_war.tip.squad.rabbit"),
        SUPPORT("message.gamemode.three_way_war.tip.squad.support");

        private final String langKey;

        Squad(String langKey) {
            this.langKey = langKey;
        }
    }
}
