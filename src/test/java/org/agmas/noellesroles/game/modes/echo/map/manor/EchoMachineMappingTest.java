package org.agmas.noellesroles.game.modes.echo.map.manor;

import net.minecraft.core.Direction;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachines.Kind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 只验证机器描述与布局映射，不启动注册表或游戏世界。 */
class EchoMachineMappingTest {
    @Test
    void allTwelveIdsAndNameKeysMatchThePublicContract() {
        Set<String> paths = Set.of("echo_beacon", "echo_core_beacon", "echo_crystal_extractor",
                "echo_key_altar", "echo_supply_crate", "echo_black_market", "echo_black_crystal_extractor",
                "echo_black_dropoff", "echo_core_console", "echo_free_exit", "echo_evacuation_console", "echo_broker_exit");
        assertEquals(12, Kind.values().length);
        assertEquals(paths, Arrays.stream(Kind.values()).map(kind -> kind.id().getPath()).collect(Collectors.toSet()));
        for (Kind kind : Kind.values()) {
            assertEquals("noellesroles", kind.id().getNamespace());
            assertEquals("block.noellesroles." + kind.id().getPath(), kind.nameKey());
        }
    }

    @Test
    void everyMarkerMapsToExactlyOneMachineAndAllTwelveKindsAreUsed() {
        var layout = EchoManorLayout.build();
        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (var marker : layout.markers()) {
            Kind kind = EchoManorScene.machineKind(layout, marker.x(), marker.y(), marker.z());
            assertNotNull(kind, marker.id());
            counts.merge(kind, 1, Integer::sum);
        }
        assertEquals(Map.ofEntries(
                Map.entry(Kind.BEACON, 2), Map.entry(Kind.CORE_BEACON, 1), Map.entry(Kind.CRYSTAL, 6),
                Map.entry(Kind.ALTAR, 3), Map.entry(Kind.SUPPLY, 10), Map.entry(Kind.BLACK_MARKET, 2),
                Map.entry(Kind.BLACK_NODE, 2), Map.entry(Kind.BLACK_DROPOFF, 2), Map.entry(Kind.CORE_CONSOLE, 2),
                Map.entry(Kind.FREE_EXIT, 1), Map.entry(Kind.EVACUATION_CONSOLE, 1), Map.entry(Kind.BROKER_EXIT, 1)), counts);
        for (var beacon : layout.beacons()) {
            assertEquals(beacon.region().equals("core") ? Kind.CORE_BEACON : Kind.BEACON,
                    EchoManorScene.machineKind(layout, beacon.x(), EchoManorLayout.FEET_Y, beacon.z()));
        }
        assertEquals(Kind.EVACUATION_CONSOLE, EchoManorScene.machineKind(layout,
                layout.evacControl().x(), 5, layout.evacControl().z()));
    }

    @Test
    void everyMachineFacesAnAdjacentWalkableStandingCell() {
        var layout = EchoManorLayout.build();
        for (var marker : layout.markers()) {
            Direction facing = EchoManorScene.machineFacing(layout, marker.x(), marker.y(), marker.z());
            int x = marker.x() + facing.getStepX(), z = marker.z() + facing.getStepZ();
            assertTrue(layout.solid(x, EchoManorLayout.GROUND_Y, z), marker.id());
            assertFalse(layout.solid(x, EchoManorLayout.FEET_Y, z), marker.id());
            assertFalse(layout.solid(x, EchoManorLayout.FEET_Y + 1, z), marker.id());
        }
        // 面板正面必须位于门外，不能朝着封闭金库或撤离检测区；坐标取自布局本身。
        for (var door : layout.doors()) {
            assertEquals(door.panelX() < layout.coreArea().minX() ? Direction.WEST : Direction.EAST,
                    EchoManorScene.machineFacing(layout, door.panelX(), 5, door.panelZ()), "core/" + door.id());
            assertEquals(EchoManorLayout.Kind.GATE, layout.at(door.panelX(), EchoManorLayout.FEET_Y, door.panelZ() + 1),
                    "core/" + door.id() + " 门体不是铁块");
        }
        assertEquals(Direction.WEST, EchoManorScene.machineFacing(layout,
                layout.evacControl().x(), 5, layout.evacControl().z()));
    }

    @Test
    void sceneryAndSolidGateBodiesAreNeverMachines() {
        var layout = EchoManorLayout.build();
        for (int y = 0; y < EchoManorLayout.HEIGHT; y++) {
            for (int x = 0; x < EchoManorLayout.WIDTH; x++) {
                for (int z = 0; z < EchoManorLayout.DEPTH; z++) {
                    if (!layout.at(x, y, z).marker()) {
                        assertNull(EchoManorScene.machineKind(layout, x, y, z));
                    }
                }
            }
        }
        for (var door : layout.doors()) {
            assertEquals(EchoManorLayout.Kind.GATE,
                    layout.at(door.panelX(), EchoManorLayout.FEET_Y, door.panelZ() + 1), "core/" + door.id());
        }
        assertEquals(EchoManorLayout.Kind.GATE,
                layout.at(layout.evacControl().x(), EchoManorLayout.FEET_Y, layout.evacControl().z() + 1));
    }
}
