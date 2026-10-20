package org.agmas.noellesroles.game.modes.echo.hunter;

import org.agmas.noellesroles.game.modes.echo.combat.EchoHuntLoop;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 追猎者两个职业的纯逻辑验收：连段窗口、弹道、场地、被动与技能映射。
 *
 * <p>只测纯状态机与纯函数；世界判定（射线、传送、属性修正）不在单元测试范围内，
 * 那部分依赖真实服务端，属于手动验收。
 */
class EchoHunterClassTest {

    // ───────────────────────── 职业身份 ─────────────────────────

    @Test void twoClassesHaveDistinctIdentityAndSkill() {
        EchoHunterClass[] classes = EchoHunterClass.values();
        assertEquals(2, classes.length);
        assertNotEquals(classes[0].id(), classes[1].id());
        assertNotEquals(classes[0].skill(), classes[1].skill());
        for (EchoHunterClass hunter : classes) {
            assertNotNull(hunter.skill());
            assertEquals(hunter, EchoHunterClass.byId(hunter.id()).orElseThrow());
            assertTrue(hunter.nameKey().startsWith("echo.hunter."));
            assertTrue(hunter.descriptionKey().startsWith("echo.hunter."));
            assertTrue(hunter.passiveNameKey().startsWith("echo.hunter."));
            assertTrue(hunter.passiveDescriptionKey().startsWith("echo.hunter."));
            assertTrue(hunter.attackNameKey().startsWith("echo.hunter."));
            assertTrue(hunter.attackDescriptionKey().startsWith("echo.hunter."));
        }
    }

    @Test void defaultAssignmentAlternatesSoTwoHuntersDiffer() {
        assertNotEquals(EchoHunterClass.defaultFor(0), EchoHunterClass.defaultFor(1));
        assertEquals(EchoHunterClass.defaultFor(0), EchoHunterClass.defaultFor(2));
        assertEquals(EchoHunterClass.defaultFor(1), EchoHunterClass.defaultFor(3));
        // 负数下标不能崩：floorMod 保证始终落在合法区间。
        assertNotNull(EchoHunterClass.defaultFor(-1));
    }

    @Test void classOnlyAppliesToHunterSpecialization() {
        assertTrue(EchoHunterClass.accepts(EchoSpecialization.HUNTER));
        assertFalse(EchoHunterClass.accepts(EchoSpecialization.SABOTEUR));
        assertFalse(EchoHunterClass.accepts(EchoSpecialization.PORTER));
        assertNull(EchoHunterClass.faction(EchoFaction.CARRIER));
        assertNotNull(EchoHunterClass.faction(EchoFaction.INTERCEPTOR));
        assertTrue(EchoHunterClass.REND.ranged() == false);
        assertTrue(EchoHunterClass.WEAVER.ranged());
    }

    // ───────────────────────── 裂爪连段 ─────────────────────────

    @Test void comboAdvancesOnHitAndResetsOnFinisher() {
        EchoRendCombo combo = new EchoRendCombo();
        assertEquals(1, combo.swing(0).stage());
        assertFalse(combo.swing(0).finisher());

        EchoRendCombo.Swing first = combo.swing(0);
        combo.onHit(0, first);
        assertEquals(2, combo.stage());

        EchoRendCombo.Swing second = combo.swing(10);
        assertEquals(2, second.stage());
        assertEquals(1, second.bleedStacks());
        combo.onHit(10, second);
        assertEquals(3, combo.stage());
        assertTrue(combo.finisherReady());

        EchoRendCombo.Swing third = combo.swing(20);
        assertTrue(third.finisher());
        assertEquals(EchoRendCombo.BLEED_STACKS[2], third.bleedStacks());
        combo.onHit(20, third);
        assertEquals(1, combo.stage(), "终结技打完后回到第一段");
        assertEquals(0, combo.windowUntil());
    }

    @Test void missAlwaysResetsTheChain() {
        EchoRendCombo combo = new EchoRendCombo();
        combo.onHit(0, combo.swing(0));
        combo.onHit(10, combo.swing(10));
        assertEquals(3, combo.stage());
        combo.onMiss(15);
        assertEquals(1, combo.stage());
    }

    @Test void finisherCostsMoreRecoveryThanPlainHits() {
        assertTrue(EchoRendCombo.OPENING.recoverTicks() > 0);
        for (int stage = 2; stage <= EchoRendCombo.MAX_STAGE; stage++) {
            assertTrue(new EchoRendCombo.Swing(stage, stage == EchoRendCombo.MAX_STAGE, 0, 0).recoverTicks()
                    >= new EchoRendCombo.Swing(stage - 1, false, 0, 0).recoverTicks(), "后摇必须随段数不减");
        }
        assertTrue(EchoRendCombo.REACH_BONUS[2] > EchoRendCombo.REACH_BONUS[1]);
    }

    @Test void skillPushesComboStraightToFinisher() {
        EchoRendCombo combo = new EchoRendCombo();
        combo.forceFinisher(100);
        assertTrue(combo.finisherReady());
        assertEquals(3, combo.swing(100).stage());
        // 窗口过了就掉回第一段，技能不能变成永久终结技
        assertEquals(1, combo.swing(100 + EchoRendCombo.WINDOW_TICKS + 1).stage());
    }

    @Test void comboBreaksWhenWindowExpires() {
        EchoRendCombo combo = new EchoRendCombo();
        combo.onHit(0, combo.swing(0));
        assertEquals(2, combo.stage());
        // 窗口内仍然连着
        assertEquals(2, combo.swing(EchoRendCombo.WINDOW_TICKS - 1).stage());
        // 到达窗口边界立刻断连
        EchoRendCombo.Swing stale = combo.swing(EchoRendCombo.WINDOW_TICKS);
        assertEquals(1, stale.stage());
        assertFalse(stale.finisher());
        assertEquals(1, combo.stage(), "swing 必须把过期状态写回，不能只是返回值");
    }

    // ───────────────────────── 织网弹道 ─────────────────────────

    @Test void boltFliesForwardAndDropsUnderGravity() {
        UUID owner = UUID.randomUUID();
        List<EchoWebBolt.Point> path = EchoWebBolt.trajectory(owner, 0, 64, 0, 1, 0, 0, 10);
        assertEquals(11, path.size());
        EchoWebBolt.Point start = path.get(0);
        EchoWebBolt.Point end = path.get(path.size() - 1);
        assertTrue(end.x() > start.x() + 5, "十 tick 内应飞出数格");
        assertTrue(end.y() < start.y(), "必须受重力下坠");
        assertEquals(0D, end.z(), 1.0E-6, "正前方射击不应侧偏");
    }

    @Test void boltSpeedMatchesConstantAndNormalisesDirection() {
        EchoWebBolt bolt = EchoWebBolt.aim(UUID.randomUUID(), 0, 0, 0, 0, 0, 4);
        assertEquals(EchoWebBolt.SPEED, Math.abs(bolt.vz()), 1.0E-9, "方向必须先归一化");
        // 零向量退化时也要给出合法弹道，不能产生 NaN
        EchoWebBolt degenerate = EchoWebBolt.aim(UUID.randomUUID(), 0, 0, 0, 0, 0, 0);
        assertTrue(Double.isFinite(degenerate.vz()) && degenerate.vz() != 0);
    }

    @Test void boltExpiresAndStopsAdvancing() {
        EchoWebBolt bolt = EchoWebBolt.aim(UUID.randomUUID(), 0, 64, 0, 1, 0, 0);
        for (int i = 0; i < EchoWebBolt.MAX_TICKS; i++) bolt.advanceTick();
        assertTrue(bolt.expired());
        assertTrue(bolt.finished());
        double frozen = bolt.x();
        bolt.step();
        assertEquals(frozen, bolt.x(), 1.0E-9, "结束的弹不再移动");
    }

    @Test void boltHitSphereIncludesPlayerWidth() {
        EchoWebBolt bolt = EchoWebBolt.aim(UUID.randomUUID(), 0, 0, 0, 1, 0, 0);
        assertTrue(bolt.reaches(0, 0, 0, 0.3), "起点自身必须在判定球内");
        assertFalse(bolt.reaches(20, 0, 0, 0.3));
    }

    // ───────────────────────── 黏网场地 ─────────────────────────

    @Test void splatAndNestDifferInSizeDurationAndSlow() {
        EchoWebField splat = new EchoWebField(UUID.randomUUID(), EchoWebField.Kind.SPLAT, 0, 64, 0, 0);
        EchoWebField nest = new EchoWebField(UUID.randomUUID(), EchoWebField.Kind.NEST, 0, 64, 0, 0);
        assertTrue(nest.radius() > splat.radius());
        assertTrue(nest.slow() < splat.slow(), "蛛网阵减速更强");
        assertTrue(nest.expiresAt() > splat.expiresAt());
        assertTrue(nest.holdsFocus());
        assertFalse(splat.holdsFocus());
        assertTrue(splat.bindTicks() > 0);
        assertEquals(0, nest.bindTicks());
    }

    @Test void fieldContainsWithinRadiusOnlyAndExpires() {
        EchoWebField field = new EchoWebField(UUID.randomUUID(), EchoWebField.Kind.SPLAT, 10, 64, 10, 100);
        assertTrue(field.contains(10, 10));
        assertTrue(field.contains(10 + EchoWebField.SPLAT_RADIUS - 0.01, 10));
        assertFalse(field.contains(10 + EchoWebField.SPLAT_RADIUS + 0.01, 10));
        assertTrue(field.covers(100, 10, 10));
        assertFalse(field.covers(field.expiresAt(), 10, 10), "到期后不再覆盖");
    }

    // ───────────────────────── 被动 ─────────────────────────

    @Test void rendPassiveOnlyBoostsWhenPreyIsBleeding() {
        assertEquals(0D, EchoHunterState.rendGain(0D, true), 1.0E-9, "无目标时不能凭空产生锁定");
        double plain = EchoHunterState.rendGain(2D, false);
        double boosted = EchoHunterState.rendGain(2D, true);
        assertEquals(2D, plain, 1.0E-9);
        assertEquals(2D * EchoHunterState.REND_FOCUS_BOOST, boosted, 1.0E-9);
    }

    @Test void weaverPassiveNeedsStillTargetInsideRange() {
        assertEquals(0D, EchoHunterState.weaverGain(0D, 999, 1), 1.0E-9);
        double moving = EchoHunterState.weaverGain(2D, EchoHunterState.WEAVER_STILL_TICKS - 1, 5);
        assertEquals(2D, moving, 1.0E-9, "没静止够久就不加成");
        double still = EchoHunterState.weaverGain(2D, EchoHunterState.WEAVER_STILL_TICKS, 5);
        assertEquals(2D * EchoHunterState.WEAVER_FOCUS_BOOST, still, 1.0E-9);
        double faraway = EchoHunterState.weaverGain(2D, 999, EchoHunterState.WEAVER_STILL_RANGE + 1);
        assertEquals(2D, faraway, 1.0E-9, "超出感知半径不加成");
    }

    @Test void boundPreyGetsExtraDownedTime() {
        assertEquals(0, EchoHunterState.boundBleed(false));
        assertTrue(EchoHunterState.boundBleed(true) > 0);
    }

    // ───────────────────────── 运行时容器 ─────────────────────────

    @Test void hunterStateTracksBoltsFieldsAndPrunes() {
        EchoHunterState state = new EchoHunterState();
        UUID owner = UUID.randomUUID();
        state.addBolt(EchoWebBolt.aim(owner, 0, 64, 0, 1, 0, 0));
        assertEquals(1, state.bolts().size());
        assertTrue(state.canThrow());
        for (int i = 0; i < EchoHunterState.MAX_BOLTS; i++) state.addBolt(EchoWebBolt.aim(owner, 0, 64, 0, 1, 0, 0));
        assertFalse(state.canThrow(), "在飞弹数量必须封顶");

        EchoWebField nest = new EchoWebField(owner, EchoWebField.Kind.NEST, 0, 64, 0, 0);
        state.addField(nest);
        assertTrue(state.holdsFocusAt(0, 0, 0));
        assertFalse(state.holdsFocusAt(nest.expiresAt(), 0, 0));

        EchoWebField splat = new EchoWebField(owner, EchoWebField.Kind.SPLAT, 0, 64, 0, 0);
        state.addField(splat);
        assertEquals(EchoWebField.SPLAT_BIND_TICKS, state.bindTicksAt(0, 0, 0));

        state.bolts().forEach(EchoWebBolt::finish);
        state.prune(nest.expiresAt() + 1);
        assertTrue(state.bolts().isEmpty(), "结束的弹必须被清掉");
        assertTrue(state.fields().isEmpty(), "过期场地必须被清掉");

        state.clear();
        assertTrue(state.bolts().isEmpty() && state.fields().isEmpty());
        assertEquals(1, state.combo().stage());
    }

    @Test void focusHoldOnlyAppliesInsideOwnNest() {
        EchoHuntLoop loop = new EchoHuntLoop();
        loop.tick(0, EchoHuntLoop.FOCUS_MAX, false);
        assertEquals(EchoHuntLoop.FOCUS_MAX, loop.focus());
        // 无目标：默认衰减
        loop.tick(1, 0, false);
        assertTrue(loop.focus() < EchoHuntLoop.FOCUS_MAX);
        // 阵内：不衰减
        int held = loop.focus();
        loop.tick(2, 0, true);
        assertEquals(held, loop.focus(), "蛛网阵内锁定值不衰减");
        // 蓄力相仍然照常消耗，否则阵内蹲守会变成无限满锁定
        loop.onCharge(3);
        loop.tick(4, 0, true);
        assertTrue(loop.focus() < held);
    }

    @Test void everyHunterClassKeyExistsInThreeLanguages() throws Exception {
        for (String language : List.of("zh_cn", "zh_tw", "en_us")) {
            var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/resources/assets/echo_freight/lang/" + language + ".json"),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (EchoHunterClass hunter : EchoHunterClass.values()) {
                for (String key : List.of(hunter.nameKey(), hunter.descriptionKey(), hunter.passiveNameKey(),
                        hunter.passiveDescriptionKey(), hunter.attackNameKey(), hunter.attackDescriptionKey(),
                        hunter.skill().nameKey(), hunter.skill().descriptionKey()))
                    assertTrue(json.has(key), key + " @ " + language);
            }
            for (String key : List.of("echo.hunter.locked", "echo.hunter.selected", "echo.hunter.none"))
                assertTrue(json.has(key), key + " @ " + language);
        }
    }

    @Test void everyHunterSkillIconExistsOnDisk() throws Exception {
        for (EchoHunterClass hunter : EchoHunterClass.values()) {
            String icon = hunter.skill().icon();
            String path = icon.substring(icon.indexOf(':') + 1);
            assertTrue(java.nio.file.Files.isRegularFile(
                    java.nio.file.Path.of("src/main/resources/assets/noellesroles/" + path)),
                    "缺少技能图标 " + icon);
        }
    }
}
