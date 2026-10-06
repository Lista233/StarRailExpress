package org.agmas.noellesroles.role.qust;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.event.OnTeammateKilledTeammate;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.XiaoNaoHandler;
import org.agmas.noellesroles.role.qust.roles.american_police.AmericanPolicePlayerComponent;
import org.agmas.noellesroles.role.qust.roles.dragon_girl.DragonGirlPlayerComponent;
import org.agmas.noellesroles.role.qust.roles.pressure_monster.PressureMonsterPlayerComponent;
import org.agmas.noellesroles.role.qust.roles.minigame_master.MinigameMasterPlayerComponent;
import org.agmas.noellesroles.role.qust.roles.minigame_master.ItemMinigamePayload;
import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderPlayerComponent;
import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderPayload;
import org.agmas.noellesroles.role.qust.roles.wanderer.WandererPlayerComponent;
import org.agmas.noellesroles.role.qust.roles.wanderer.WandererPayload;
import org.agmas.noellesroles.role.qust.roles.wanderer.WandererRole;
import org.agmas.noellesroles.role.qust.roles.super_doctor.SuperDoctorPlayerComponent;
import org.agmas.noellesroles.role.qust.roles.super_doctor.SuperDoctorPayload;

/**
 * QUST 技能/事件注册入口。
 * <p>
 * 在 {@code AAAHandlerFather.register()} 中加一行 {@code QUSTHandlers.register()} 即可。
 */
public class QUSTHandlers {

    public static void register() {
        QUSTConfig.HANDLER.load();       // 确保配置文件生成
        QUSTRoles.init();                // 触发类加载 → 静态字段注册职业
        registerDragonGirlSkills();
        registerAmericanPoliceSkills();
        registerAmericanPoliceEvents();
        registerPressureMonsterSkills();
        registerPressureMonsterEvents();
        registerMinigameMasterCompleteHandler();
        registerSuperRecorderEvents();
        registerSuperRecorderMarkHandler();
        registerWandererSkills();
        registerWandererEvents();
        registerWandererNetworkHandlers();
        registerSuperDoctorSkills();
        registerSuperDoctorNetworkHandlers();
        registerBettorNetworkHandlers();
        registerHackerEvents();
    }

    // ==================== 龙娘技能注册 ====================

    private static void registerDragonGirlSkills() {
        RoleSkill.register(QUSTRoles.DRAGON_GIRL,
                // 技能1: 龙娘魅惑 (G键)
                RoleSkill.skill(
                                SRE.id("dragon_girl_charm"),
                                "skill.noellesroles.dragon_girl.charm",
                                (context) -> {
                                    DragonGirlPlayerComponent comp = QUSTComponentKeys.Keys.DRAGON_GIRL.get(context.player());
                                    return comp.useCharm();
                                }
                        ).announceToSelf()
                        .showOnHud(true)
                        .build(),

                // 技能2: 恶龙咆哮 (Shift+G)
                RoleSkill.skill(
                                SRE.id("dragon_girl_roar"),
                                "skill.noellesroles.dragon_girl.roar",
                                (context) -> {
                                    DragonGirlPlayerComponent comp = QUSTComponentKeys.Keys.DRAGON_GIRL.get(context.player());
                                    return comp.useRoar(context);
                                }
                        ).shifted(true)
                        .announceToSelf()
                        .showOnHud(true)
                        .build()
        );
    }

    // ==================== 美国警察技能注册 ====================

    private static void registerAmericanPoliceSkills() {
        RoleSkill.register(QUSTRoles.AMERICAN_POLICE,
                RoleSkill.skill(
                                QUSTRoles.AMERICAN_POLICE_SKILL_ID,
                                "skill.noellesroles.american_police.mark",
                                (context) -> {
                                    var target = context.target();
                                    if (target == null) {
                                        context.player().displayClientMessage(
                                                net.minecraft.network.chat.Component.translatable(
                                                        "message.american_police.no_target"),
                                                true);
                                        return false;
                                    }
                                    var targetPlayer = context.player().level().getPlayerByUUID(target);
                                    if (targetPlayer == null) {
                                        context.player().displayClientMessage(
                                                net.minecraft.network.chat.Component.translatable(
                                                        "message.american_police.no_target"),
                                                true);
                                        return false;
                                    }
                                    AmericanPolicePlayerComponent comp =
                                            QUSTComponentKeys.Keys.AMERICAN_POLICE.get(context.player());
                                    return comp.useSkill(targetPlayer);
                                }
                        ).withTarget()
                        .announceToSelf()
                        .showOnHud(true)
                        .cooldownSeconds(60)
                        .charges(1)
                        .build()
        );
    }

    // ==================== 美国警察事件注册 ====================

    private static void registerAmericanPoliceEvents() {
        // 击杀奖励：击杀杀手/中立角色时增加技能使用次数
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReason, forceDeath) -> {
            if (killer == null) return TrueFalseResult.PASS;
            if (!org.agmas.noellesroles.utils.RoleUtils.isPlayerTheJob(killer, QUSTRoles.AMERICAN_POLICE))
                return TrueFalseResult.PASS;

            // 击杀奖励逻辑（击杀杀手/中立角色增加技能次数）
            QUSTComponentKeys.Keys.AMERICAN_POLICE.get(killer).onKillPlayer(victim);
            return TrueFalseResult.PASS;
        });
    }

    // ==================== 压力怪技能注册 ====================

    private static void registerPressureMonsterSkills() {
        RoleSkill.register(QUSTRoles.PRESSURE_MONSTER,
                RoleSkill.skill(
                                QUSTRoles.PRESSURE_MONSTER_SKILL_ID,
                                "skill.qust.pressure_monster.pressure",
                                (context) -> {
                                    var player = context.player();
                                    double radius = QUSTConfig.instance().pressureMonsterSkillRadius;
                                    double reductionPercent = QUSTConfig.instance().pressureMonsterSanReductionPercent;

                                    boolean hitAny = false;
                                    for (var target : player.level().getEntitiesOfClass(
                                            net.minecraft.world.entity.player.Player.class,
                                            player.getBoundingBox().inflate(radius))) {
                                        if (target.equals(player)) continue;
                                        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target))
                                            continue;
                                        var mood = SREPlayerMoodComponent.KEY.get(target);
                                        float currentMood = mood.getMood();
                                        if (currentMood > 0.01f) {
                                            mood.addMood(-currentMood * (float) reductionPercent);
                                            hitAny = true;
                                        }
                                    }

                                    // 播放看守者音效（监守者心跳声）
                                    player.level().playSound(null, player.blockPosition(),
                                            SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 1.0F);

                                    if (hitAny) {
                                        player.displayClientMessage(
                                                net.minecraft.network.chat.Component.translatable(
                                                        "message.pressure_monster.skill_used"),
                                                true);
                                    }
                                    return true;
                                }
                        ).announceToSelf()
                        .showOnHud(true)
                        .cooldownSeconds(QUSTConfig.instance().pressureMonsterSkillCooldownSeconds)
                        .charges(1)
                        .build()
        );
    }

    // ==================== 压力怪事件注册 ====================

    private static void registerPressureMonsterEvents() {
        // 角色分配时设置初始 60s 冷却
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (role.identifier().equals(QUSTRoles.PRESSURE_MONSTER_ID)) {
                int initialCd = QUSTConfig.instance().pressureMonsterInitialCooldownSeconds * 20;
                io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(player)
                        .setSkillCooldown(QUSTRoles.PRESSURE_MONSTER_SKILL_ID, initialCd);
            }
        });

        // 击杀事件：压力怪击杀玩家减少 30s CD
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReason, forceDeath) -> {
            if (killer == null) return TrueFalseResult.PASS;
            if (!org.agmas.noellesroles.utils.RoleUtils.isPlayerTheJob(killer, QUSTRoles.PRESSURE_MONSTER))
                return TrueFalseResult.PASS;
            QUSTComponentKeys.Keys.PRESSURE_MONSTER.get(killer).onKillPlayer();
            return TrueFalseResult.PASS;
        });

        // 小脑事件：全场触发小脑时压力怪减少 90s CD
        OnTeammateKilledTeammate.EVENT.register((victim, killer, isInnocent, deathReason) -> {
            if (killer == null) return;
            if (!XiaoNaoHandler.isXiaoNaoReason(deathReason)) return;

            var gameWorldComponent = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(victim.level());

            boolean isXiaonao = false;
            if (isInnocent) {
                var victimRole = gameWorldComponent.getRole(victim);
                var killerRole = gameWorldComponent.getRole(killer);
                if (victimRole != null && killerRole != null
                        && !victimRole.isNeutrals() && !killerRole.isNeutrals()) {
                    isXiaonao = true;
                }
            } else {
                var victimRole = gameWorldComponent.getRole(victim);
                var killerRole = gameWorldComponent.getRole(killer);
                if (victimRole != null && killerRole != null
                        && victimRole.isKiller() && !victimRole.isNeutrals()
                        && killerRole.isKiller() && !killerRole.isNeutrals()) {
                    isXiaonao = true;
                }
            }

            if (!isXiaonao) return;

            for (ServerPlayer p : victim.serverLevel().players()) {
                if (gameWorldComponent.isRole(p, QUSTRoles.PRESSURE_MONSTER)) {
                    var comp = QUSTComponentKeys.Keys.PRESSURE_MONSTER.maybeGet(p).orElse(null);
                    if (comp != null) {
                        comp.onXiaonaoTriggered();
                    }
                }
            }
        });
    }

    // ==================== 小游戏达人完成奖励处理 ====================

    private static void registerMinigameMasterCompleteHandler() {
        // 华容道挑战通关属于「自定义胜利」：由小游戏达人带领义警与平民（winWithInnocent）
        // 一同获胜。CUSTOM 状态下默认只有 CustomWinnerID 匹配的职业才算赢，这里通过
        // AllowPlayerWin 事件把整个乘客阵营（平民 + 义警）也判为获胜，与 PASSENGERS 一致。
        io.wifi.starrailexpress.event.AllowPlayerWin.EVENT.register(
                (world, player, playerRole, winStatus, roundEnd, gameComponent) -> {
                    if ((winStatus == io.wifi.starrailexpress.game.GameUtils.WinStatus.CUSTOM
                            || winStatus == io.wifi.starrailexpress.game.GameUtils.WinStatus.CUSTOM_COMPONENT)
                            && roundEnd != null
                            && "minigame_master".equals(roundEnd.CustomWinnerID)
                            && playerRole != null && playerRole.winWithInnocent()) {
                        return TrueFalseResult.TRUE;
                    }
                    return TrueFalseResult.PASS;
                });
        // 注册 C2S 全局接收器：小游戏完成后发放金币奖励或触发胜利
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                ItemMinigamePayload.CompleteItemGame.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    player.getServer().execute(() -> {
                        MinigameMasterPlayerComponent comp =
                                QUSTComponentKeys.Keys.MINIGAME_MASTER.maybeGet(player).orElse(null);
                        if (comp == null) return;

                        // 华容道挑战模式：通关后消耗挑战物品，平民与义警阵营胜利
                        if (payload.challenge()) {
                            // 防伪造：必须实际持有华容道挑战物品，通关才消耗
                            if (!consumeKlotskiChallengeItem(player)) {
                                return;
                            }
                            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                                // 自定义胜利：CUSTOM + CustomWinnerID="minigame_master"，绿色标题
                                // "我们是小游戏达人！"（announcement.star.win.minigame_master）。
                                // 义警与平民阵营由上面注册的 AllowPlayerWin 事件判为获胜。
                                org.agmas.noellesroles.utils.RoleUtils.customWinnerWin(
                                        sl, io.wifi.starrailexpress.game.GameUtils.WinStatus.CUSTOM,
                                        "minigame_master", java.util.OptionalInt.of(
                                                new java.awt.Color(54, 229, 27).getRGB()));
                            }
                            return;
                        }

                        // 普通小游戏：根据类型决定奖励金额
                        String minigameId = payload.minigameId();
                        QUSTConfig cfg = QUSTConfig.instance();
                        int reward = "klotski".equals(minigameId)
                                ? cfg.minigameMasterKlotskiCoinReward
                                : cfg.minigameMasterCoinReward;
                        io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(player).addToBalance(reward);

                        // 完成反馈：粒子 + 音效
                        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                    player.getX(), player.getY() + 1, player.getZ(),
                                    20, 0.4, 0.5, 0.4, 0.0);
                            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                                    player.getX(), player.getY() + 1.3, player.getZ(),
                                    14, 0.3, 0.4, 0.3, 0.1);
                        }
                        player.level().playSound(null, player.blockPosition(),
                                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable(
                                        "message.minigame_master.minigame_completed", reward)
                                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                                true);
                    });
                });
    }

    /**
     * 消耗玩家物品栏中的一个华容道挑战物品（仅在通关时调用）
     *
     * @return 是否找到并消耗了物品（未持有则返回 false，用于防伪造包）
     */
    private static boolean consumeKlotskiChallengeItem(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            if (stack.is(org.agmas.noellesroles.init.ModItems.KLOTSKI_CHALLENGE)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ==================== 超级记录员事件注册 ====================

    private static void registerSuperRecorderEvents() {
        // 角色分配时给予记录员笔记 + 假枪
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (!role.identifier().equals(QUSTRoles.SUPER_RECORDER_ID)) return;
            if (!(player instanceof ServerPlayer sp)) return;
            // 给予记录员笔记
            org.agmas.noellesroles.utils.RoleUtils.insertStackInFreeSlot(
                    sp, org.agmas.noellesroles.init.ModItems.WRITTEN_NOTE.getDefaultInstance());
            // 给予假枪
            org.agmas.noellesroles.utils.RoleUtils.insertStackInFreeSlot(
                    sp, org.agmas.noellesroles.init.ModItems.FAKE_REVOLVER.getDefaultInstance());
        });

        // 死亡感知：15 格半径内玩家死亡时记录位置
        io.wifi.starrailexpress.event.OnPlayerDeathWithBody.EVENT.register(
                (victim, killer, deathReason, body) -> {
                    for (ServerPlayer sp : victim.level().getServer().getPlayerList().getPlayers()) {
                        var comp = QUSTComponentKeys.Keys.SUPER_RECORDER.maybeGet(sp).orElse(null);
                        if (comp == null) continue;
                        double range = QUSTConfig.instance().superRecorderDeathSenseRange;
                        if (sp.distanceTo(victim) <= range) {
                            comp.addDeathLocation(victim.position());
                            sp.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable(
                                            "message.super_recorder.death_sensed",
                                            victim.getName().getString())
                                            .withStyle(net.minecraft.ChatFormatting.DARK_RED),
                                    true);
                        }
                    }
                });

        // 死亡事件：超级记录员死亡时清空尸体物品栏
        io.wifi.starrailexpress.event.OnPlayerDeathWithBody.EVENT.register(
                (victim, killer, deathReason, body) -> {
                    if (org.agmas.noellesroles.utils.RoleUtils.isPlayerTheJob(victim, QUSTRoles.SUPER_RECORDER)) {
                        body.setCorpseInventoryFromPlayerInventory(null);
                    }
                });
    }

    // ==================== 超级记录员标记处理 ====================

    private static void registerSuperRecorderMarkHandler() {
        // C2S：处理标记请求
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                SuperRecorderPayload.MarkPlayer.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    player.getServer().execute(() -> {
                        var comp = QUSTComponentKeys.Keys.SUPER_RECORDER.maybeGet(player).orElse(null);
                        if (comp == null) return;
                        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) return;

                        java.util.UUID targetUuid = payload.targetUuid();
                        net.minecraft.resources.ResourceLocation roleId =
                                net.minecraft.resources.ResourceLocation.tryParse(payload.roleId());
                        if (roleId == null) return;

                        // 已标记过则跳过
                        if (comp.isMarked(targetUuid)) {
                            player.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable(
                                            "message.super_recorder.already_marked")
                                            .withStyle(net.minecraft.ChatFormatting.RED),
                                    true);
                            return;
                        }

                        // 验证猜测是否正确
                        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                        var target = player.level().getPlayerByUUID(targetUuid);
                        if (target == null) return;

                        var actualRole = gameWorld.getRole(target);
                        if (actualRole != null && actualRole.identifier().equals(roleId)) {
                            // 标记成功
                            comp.addMark(targetUuid, roleId);
                            player.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable(
                                            "message.super_recorder.mark_success",
                                            target.getName().getString())
                                            .withStyle(net.minecraft.ChatFormatting.GREEN),
                                    true);
                            // 粒子反馈
                            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                                        player.getX(), player.getY() + 1, player.getZ(),
                                        15, 0.3, 0.5, 0.3, 0.1);
                            }
                        } else {
                            // 标记失败：增加失败计数（达到 5 次立即死亡）
                            comp.incrementWrongMarkCount();
                            int remaining = SuperRecorderPlayerComponent.MAX_WRONG_MARKS - comp.getWrongMarkCount();
                            player.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable(
                                            "message.super_recorder.mark_fail",
                                            target.getName().getString())
                                            .withStyle(net.minecraft.ChatFormatting.RED),
                                    true);
                            if (remaining > 0) {
                                player.displayClientMessage(
                                        net.minecraft.network.chat.Component.translatable(
                                                "message.super_recorder.wrong_marks_remaining", remaining)
                                                .withStyle(net.minecraft.ChatFormatting.YELLOW),
                                        true);
                            }
                        }
                    });
                });

        // C2S：真相之书消耗后自动标记一个未标记玩家（使用正确职业）
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                SuperRecorderPayload.AutoMarkFromTruthBook.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    player.getServer().execute(() -> {
                        var comp = QUSTComponentKeys.Keys.SUPER_RECORDER.maybeGet(player).orElse(null);
                        if (comp == null) return;
                        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) return;

                        java.util.Map<java.util.UUID, String> playerRoles = comp.getPlayerRoles();
                        if (playerRoles.isEmpty()) return;

                        // 收集所有未标记的玩家（排除记录员自己）
                        java.util.List<java.util.Map.Entry<java.util.UUID, String>> candidates = new java.util.ArrayList<>();
                        for (java.util.Map.Entry<java.util.UUID, String> entry : playerRoles.entrySet()) {
                            java.util.UUID targetUuid = entry.getKey();
                            if (comp.isMarked(targetUuid)) continue;
                            if (targetUuid.equals(player.getUUID())) continue;
                            candidates.add(entry);
                        }
                        if (candidates.isEmpty()) return;

                        // 随机选取一个未标记玩家进行自动标记
                        java.util.Map.Entry<java.util.UUID, String> picked = candidates.get(
                                player.getRandom().nextInt(candidates.size()));
                        net.minecraft.resources.ResourceLocation roleId =
                                net.minecraft.resources.ResourceLocation.tryParse(picked.getValue());
                        if (roleId == null) return;

                        comp.addMark(picked.getKey(), roleId);

                        // 获取玩家名用于提示
                        String targetName = comp.getStartPlayers().get(picked.getKey());
                        if (targetName == null) {
                            var targetPlayer = player.level().getPlayerByUUID(picked.getKey());
                            if (targetPlayer != null) targetName = targetPlayer.getName().getString();
                        }
                        if (targetName == null) targetName = picked.getKey().toString().substring(0, 8);

                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable(
                                        "message.super_recorder.truth_book_auto_marked", targetName)
                                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
                                true);
                        // 粒子反馈
                        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                                    player.getX(), player.getY() + 1, player.getZ(),
                                    15, 0.3, 0.5, 0.3, 0.1);
                        }
                    });
                });
    }

    // ==================== 游荡者技能注册 ====================

    private static void registerWandererSkills() {
        // 技能1: 灵魂出窍 (G键，存活时)
        RoleSkill.register(QUSTRoles.WANDERER,
                RoleSkill.skill(
                                QUSTRoles.WANDERER_SKILL_ID,
                                "skill.qust.wanderer.soul_out",
                                (context) -> {
                                    var player = context.player();
                                    var comp = QUSTComponentKeys.Keys.WANDERER.maybeGet(player).orElse(null);
                                    if (comp == null) return false;

                                    // 死亡后 G 键是显隐，不是灵魂出窍
                                    if (comp.isGhost()) {
                                        // 切换显隐
                                        boolean newVisible = !comp.isGhostVisible();
                                        comp.setGhostVisible(newVisible);
                                        // 同步给客户端
                                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                                                (ServerPlayer) player,
                                                new WandererPayload.GhostVisibility(newVisible));
                                        player.displayClientMessage(
                                                net.minecraft.network.chat.Component.translatable(
                                                        newVisible ? "message.wanderer_qust.ghost_visible"
                                                                : "message.wanderer_qust.ghost_hidden")
                                                        .withStyle(newVisible
                                                                ? net.minecraft.ChatFormatting.YELLOW
                                                                : net.minecraft.ChatFormatting.GRAY),
                                                true);
                                        return true;
                                    }

                                    // 灵魂出窍中按 G 主动结束
                                    if (comp.isSoulOutActive()) {
                                        comp.endSoulOut();
                                        player.displayClientMessage(
                                                net.minecraft.network.chat.Component.translatable(
                                                        "message.wanderer_qust.soul_out_ended")
                                                        .withStyle(net.minecraft.ChatFormatting.AQUA),
                                                true);
                                        return true;
                                    }

                                    if (comp.startSoulOut()) {
                                        // 通知客户端进入自由相机
                                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                                                (ServerPlayer) player,
                                                new WandererPayload.SoulOutState(true));
                                        player.displayClientMessage(
                                                net.minecraft.network.chat.Component.translatable(
                                                        "message.wanderer_qust.soul_out_start")
                                                        .withStyle(net.minecraft.ChatFormatting.AQUA),
                                                true);
                                        return true;
                                    }
                                    return false;
                                }
                        ).announceToSelf()
                        .showOnHud(false)
                        .toggleable(true)
                        .cooldownSeconds(WandererPlayerComponent.SOUL_OUT_COOLDOWN / 20)
                        .build()
        );
    }

    // ==================== 游荡者事件注册 ====================

    private static void registerWandererEvents() {
        // 幽灵被击杀：彻底死亡进入旁观者模式（替代原 Shift+G 强制死亡）。
        // 无击杀者的环境死亡（黑暗等）仅拦截，不让幽灵死亡。
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReason, forceDeath) -> {
            if (victim == null) return TrueFalseResult.PASS;
            var comp = QUSTComponentKeys.Keys.WANDERER.maybeGet(victim).orElse(null);
            if (comp == null || !comp.isGhost() || comp.isFinalDeath()) {
                return TrueFalseResult.PASS;
            }
            if (victim instanceof ServerPlayer sp) {
                WandererRole.enterFinalDeath(sp, comp);
            }
            // 阻止正常死亡链（不给击杀奖励、不生成尸体）；
            // forceDeath 拦不住时由 WandererRole.onDeath 兜底进入旁观者
            return TrueFalseResult.FALSE;
        });
    }

    // ==================== 游荡者网络处理 ====================

    private static void registerWandererNetworkHandlers() {
        // C2S: 切换幽灵显隐
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                WandererPayload.ToggleGhostVisibility.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    player.getServer().execute(() -> {
                        var comp = QUSTComponentKeys.Keys.WANDERER.maybeGet(player).orElse(null);
                        if (comp == null || !comp.isGhost()) return;
                        boolean newVisible = !comp.isGhostVisible();
                        comp.setGhostVisible(newVisible);
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                                player, new WandererPayload.GhostVisibility(newVisible));
                    });
                });
    }

    // ==================== 超级医生技能注册 ====================

    private static void registerSuperDoctorSkills() {
        // G 键治疗技能：恢复周围人 san 值
        RoleSkill.register(QUSTRoles.SUPER_DOCTOR,
                RoleSkill.skill(
                                QUSTRoles.SUPER_DOCTOR_SKILL_ID,
                                "skill.qust.super_doctor.heal",
                                (context) -> {
                                    var player = context.player();
                                    var comp = QUSTComponentKeys.Keys.SUPER_DOCTOR.maybeGet(player).orElse(null);
                                    if (comp == null || !comp.isHealReady()) return false;

                                    double radius = QUSTConfig.instance().superDoctorHealRange;
                                    float healAmount = (float) QUSTConfig.instance().superDoctorHealAmount;
                                    boolean hitAny = false;

                                    for (var target : player.level().getEntitiesOfClass(
                                            net.minecraft.world.entity.player.Player.class,
                                            player.getBoundingBox().inflate(radius))) {
                                        if (target.equals(player)) continue;
                                        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target))
                                            continue;
                                        var mood = SREPlayerMoodComponent.KEY.get(target);
                                        if (mood.getMood() < 0.99f) {
                                            mood.addMood(healAmount);
                                            hitAny = true;
                                        }
                                    }

                                    // 设置冷却
                                    int cooldownTicks = QUSTConfig.instance().superDoctorHealCooldownSeconds * 20;
                                    comp.setHealCooldownTicks(cooldownTicks);

                                    // 反馈
                                    player.level().playSound(null, player.blockPosition(),
                                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
                                    if (hitAny) {
                                        player.displayClientMessage(
                                                net.minecraft.network.chat.Component.translatable(
                                                        "message.super_doctor.heal_used"),
                                                true);
                                    }
                                    return true;
                                }
                        ).announceToSelf()
                        .showOnHud(true)
                        .cooldownSeconds(QUSTConfig.instance().superDoctorHealCooldownSeconds)
                        .charges(1)
                        .build()
        );
    }

    // ==================== 超级医生网络处理（悔改之枪） ====================

    private static void registerSuperDoctorNetworkHandlers() {
        // C2S: 悔改之枪射击处理
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                SuperDoctorPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    player.getServer().execute(() -> {
                        var game = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                        var role = game.getRole(player);
                        if (role == null || !role.identifier().equals(QUSTRoles.SUPER_DOCTOR_ID)) return;

                        // 服务端消耗物品 + CD
                        var mainHand = player.getMainHandItem();
                        if (mainHand.is(org.agmas.noellesroles.init.ModItems.REPENTANCE_GUN)) {
                            mainHand.hurtAndBreak(1, player,
                                    net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                        }
                        player.getCooldowns().addCooldown(
                                org.agmas.noellesroles.init.ModItems.REPENTANCE_GUN,
                                io.wifi.starrailexpress.SREConfig.instance().revolverCooldown * 20);

                        int targetId = payload.targetId();
                        if (targetId < 0) return; // 空枪

                        var hitEntity = player.serverLevel().getEntity(targetId);
                        if (!(hitEntity instanceof ServerPlayer target)) return;
                        if (target.distanceToSqr(player) > 30 * 30) return;

                        var victimRole = game.getRole(target);
                        if (victimRole == null) return;

                        // 杀手 → 变为随机的平民阵营角色
                        if (victimRole.isKiller()) {
                            // 获取所有平民阵营角色
                            var civilianRoles = io.wifi.starrailexpress.api.TMMRoles.ROLES.values().stream()
                                    .filter(r -> r.isInnocent() && !r.isVigilanteTeam())
                                    .toList();
                            if (!civilianRoles.isEmpty()) {
                                var randomRole = civilianRoles.get(player.level().random.nextInt(civilianRoles.size()));
                                org.agmas.noellesroles.utils.RoleUtils.changeRoleAndSendWelcome(target, randomRole);
                                player.level().playSound(null, target.blockPosition(),
                                        net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.5F);
                                player.displayClientMessage(
                                        net.minecraft.network.chat.Component.translatable(
                                                "message.super_doctor.repentance_hit_killer")
                                                .withStyle(net.minecraft.ChatFormatting.GREEN), true);
                                target.displayClientMessage(
                                        net.minecraft.network.chat.Component.translatable(
                                                "message.super_doctor.repentance_transformed")
                                                .withStyle(net.minecraft.ChatFormatting.GREEN), false);
                            }
                            return;
                        }

                        // 中立 → 正常杀死
                        if (victimRole.isNeutrals()) {
                            io.wifi.starrailexpress.game.GameUtils.killPlayer(
                                    target, true, player,
                                    io.wifi.starrailexpress.game.GameConstants.DeathReasons.GUN_SHOT);
                            player.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable(
                                            "message.super_doctor.repentance_hit_neutral")
                                            .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                            return;
                        }

                        // 平民/义警 → 不致死，超级医生受小脑惩罚
                        if (victimRole.isInnocent() || victimRole.isVigilanteTeam()) {
                            player.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable(
                                            "message.super_doctor.repentance_hit_innocent")
                                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                            io.wifi.starrailexpress.game.GameUtils.killPlayer(
                                    player, true, null,
                                    io.wifi.starrailexpress.game.GameConstants.DeathReasons.SHOT_INNOCENT);
                        }
                    });
                });
    }

    // ==================== 筹客网络处理（恶魔轮盘停止） ====================

    private static void registerBettorNetworkHandlers() {
        // C2S: 客户端在 Screen 中点击请求停止轮盘
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                org.agmas.noellesroles.role.qust.roles.bettor.BettorPayload.StopRoulette.TYPE,
                (payload, context) -> {
                    ServerPlayer sp = context.player();
                    sp.getServer().execute(() -> {
                        var game = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(sp.level());
                        if (game == null || !game.isRole(sp, QUSTRoles.BETTOR)) return;

                        var comp = QUSTComponentKeys.Keys.BETTOR.maybeGet(sp).orElse(null);
                        if (comp == null || !comp.isRolling()) return;

                        // 停止滚动，生成结果
                        int result = comp.stopAndApplyResult(sp);

                        // 播放音效
                        sp.level().playSound(null, sp.blockPosition(),
                                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

                        // 消耗物品
                        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
                        var held = sp.getItemInHand(hand);
                        if (held.is(org.agmas.noellesroles.init.ModItems.DEVIL_ROULETTE)) {
                            held.shrink(1);
                        }

                        // 发送结果给客户端
                        String desc = comp.getResultDescription();
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                                new org.agmas.noellesroles.role.qust.roles.bettor.BettorPayload.RouletteResult(result, desc));
                    });
                });
    }

    // ==================== 黑客事件注册（干扰芯片为物品，不再注册技能） ====================

    private static void registerHackerEvents() {
        // 角色分配时给予黑客干扰芯片物品
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (!role.identifier().equals(QUSTRoles.HACKER_ID)) return;
            if (!(player instanceof ServerPlayer sp)) return;
            // 给予干扰芯片
            org.agmas.noellesroles.utils.RoleUtils.insertStackInFreeSlot(
                    sp, org.agmas.noellesroles.init.ModItems.INTERFERENCE_CHIP.getDefaultInstance());
        });
    }
}
