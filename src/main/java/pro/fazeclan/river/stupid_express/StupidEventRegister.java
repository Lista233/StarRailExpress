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

package pro.fazeclan.river.stupid_express;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import io.wifi.starrailexpress.rules.ChatHudRules;
import io.wifi.starrailexpress.rules.ReplayRules;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.utils.RoleUtils;

import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.lovers.LoversWinCheckEvent;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.PlayerStatsBeforeRefugee;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistWinChecker;
import pro.fazeclan.river.stupid_express.role.initiate.SEInitiateEventHandler;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

public class StupidEventRegister {

    /**
     * 主类初始化阶段的事件 / 规则注册，从 {@link StupidExpress#onInitialize()} 中剥离，归一化到此处。
     */
    public static void registerInitEvents() {
        OnGameEnd.EVENT.register((world, cca) -> {
            world.players().forEach(serverPlayer -> {
                RemoveStatusBarPayload payload = new RemoveStatusBarPayload("loose_end");
                ServerPlayNetworking.send(serverPlayer, payload);
            });
        });
        GameInitializeEvent.EVENT.register((ServerLevel, gameWorldComponent, serverPlayers) -> {
            var refugeeC = RefugeeComponent.KEY.get(ServerLevel);
            if (refugeeC != null) {
                refugeeC.reset();
            }
        });
        register();
        LoversWinCheckEvent.register();
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
            if (gameWorldComponent.getGameMode() instanceof WTLooseEndsGameMode)
                return;
            var modifierComponent = WorldModifierComponent.KEY.get(victim.level());
            if (gameWorldComponent != null) {
                SRERole role = gameWorldComponent.getRole(victim);
                if (role != null) {
                    if (role.identifier().getPath().equals(TMMRoles.LOOSE_END.identifier().getPath())) {
                        var refugeeComponent = RefugeeComponent.KEY.get(victim.level());
                        refugeeComponent.onLooseEndDeath(victim, deathReason);
                    }
                }
            }
            if (modifierComponent != null) {
                if (modifierComponent.isModifier(victim, SEModifiers.SPLIT_PERSONALITY)) {
                    var splc = SplitPersonalityComponent.KEY.get(victim);
                    if (splc != null && !splc.isDeath()) {
                        splc.setDeath(true);
                    }
                }
            }
        });
        GameInitializeEvent.EVENT.register((ServerLevel, gameWorldComponent, serverPlayers) -> {
            serverPlayers.forEach(serverPlayer -> {
                RemoveStatusBarPayload payload = new RemoveStatusBarPayload("loose_end");
                ServerPlayNetworking.send(serverPlayer, payload);
            });
        });

        ReplayRules.cantSendReplay.add(
                (player -> {
                    WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(player.level());
                    return modifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY);
                }));
        ChatHudRules.cantUseChatHud.add(
                (player -> {
                    WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(player.level());
                    SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
                    var role = gameComponent.getRole(player);
                    return role != null && !ChatHudRules.canUseChatHud.stream().anyMatch((pre) -> pre.test(role))
                            && modifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY);
                }));

        PlayerStatsBeforeRefugee.registerDeathEvent();
    }

    public static void register() {
        // 死灵
        ArsonistWinChecker.registerEvent();
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            var component = SREGameWorldComponent.KEY.get(victim.level());
            if (component.canUseKillerFeatures(victim)) {
                var nc = NecromancerComponent.KEY.get(victim.level());
                nc.increaseAvailableRevives();
                nc.sync();
            }
        });
        // 初学
        SEInitiateEventHandler.register();

        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            LoversComponent component = LoversComponent.KEY.get(victim);

            if (!component.isLover()) {
                return;
            }

            var level = victim.level();
            var lover = level.getPlayerByUUID(component.getLover());
            if (lover != null) {
                if (GameUtils.isPlayerAliveAndSurvival(lover)) {
                    GameUtils.forceKillPlayer(
                            lover,
                            true,
                            victim, GameConstants.DeathReasons.BROKEN_HEART);
                } else if (lover instanceof ServerPlayer stashedLover
                        && PelicanManager.isStashed(stashedLover)) {
                    // 鹈鹕肚内的恋人：肚内玩家在服务端是旁观者，标准死亡管线
                    // （GameMode#killPlayer）不会结算他们，这里手动补齐「殉情而亡」
                    killStashedLover(stashedLover, victim);
                }
            }
        });
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            var cca = WorldModifierComponent.KEY.get(victim.level());
            if (cca.isModifier(victim, SEModifiers.CURSED) && killer != null) {
                cca.removeModifier(victim, SEModifiers.CURSED);
                cca.addModifier(killer, SEModifiers.CURSED);
            }
        });
    }

    /**
     * 鹈鹕肚内恋人的殉情死亡结算。
     *
     * <p>
     * 被吞玩家在服务端是旁观者，{@code GameMode#killPlayer} 的死亡管线只处理存活玩家，
     * 肚内恋人不会随伴侣死亡而殉情。这里按标准死亡顺序手动补齐：
     * 生成尸体 → 记录回放 → 广播死亡事件（{@code handleStashedPlayerDeath} 会清理肚内状态）→ 死因消息。
     */
    private static void killStashedLover(ServerPlayer lover, Player killer) {
        if (lover == null || lover.level().isClientSide()) {
            return;
        }
        ResourceLocation deathReason = GameConstants.DeathReasons.BROKEN_HEART;
        // 生成尸体（肚内玩家被固定在鹈鹕身边，尸体即落在当前位置）
        GameUtils.spawnBodyEntity(lover, killer, RoleUtils.getPlayerRole(lover), deathReason);
        // 回放记录（返回值即死因播报组件）
        Component deathMessage = SRE.REPLAY_MANAGER.recordPlayerKill(
                killer != null ? killer.getUUID() : null,
                lover.getUUID(),
                deathReason);
        // 确保处于旁观（死亡）状态
        if (lover.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            lover.setGameMode(GameType.SPECTATOR);
        }
        // 广播死亡事件：handleStashedPlayerDeath 负责清理肚内状态，其余死亡逻辑照常结算
        OnPlayerDeath.EVENT.invoker().onPlayerDeath(lover, deathReason);
        OnPlayerDeathWithKiller.EVENT.invoker().onPlayerDeath(lover, killer, deathReason);
        // 死因消息
        boolean cantSend = ReplayRules.cantSendReplay.stream().anyMatch(pre -> pre.test(lover));
        if (!cantSend) {
            lover.sendSystemMessage(Component.translatable("message.death_reason.prefix",
                    Component.literal("").withStyle(ChatFormatting.LIGHT_PURPLE).append(deathMessage))
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }
}
