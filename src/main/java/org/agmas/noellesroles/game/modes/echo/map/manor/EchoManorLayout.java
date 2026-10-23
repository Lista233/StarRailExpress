package org.agmas.noellesroles.game.modes.echo.map.manor;

import java.util.ArrayList;
import java.util.List;

/**
 * 回声货运测试地图「庄园」的纯几何布局：只描述方块种类与模式点位，不引用任何 Minecraft 类。
 *
 * <p>坐标系：局部坐标 x 0..95（西→东）、z 0..95（北→南）、y 0..13（下→上）。
 * 地面方块在 {@link #GROUND_Y}，玩家脚踩 {@link #FEET_Y}；所有交互点位都落在脚层，
 * 这样 {@code BlockPos.containing(point)} 一定命中摆放好的标记方块。
 *
 * <p>结构不变量（改动坐标时必须同时满足，{@code EchoManorLayoutTest} 会逐条验证）：
 * <ul>
 * <li>整张图只有一个地面高度：没有台阶、半砖、栅栏、梯子。路线准入的整数步行图
 * 在 {@code canJump=false} 下也必须走通，所以禁止任何 1 格高差。</li>
 * <li>金库（核心区）只有两个门洞，门体关闭时内部完全封闭；门体是整格铁块，
 * 开门就是把这些格子写成空气，不能夹带方块实体或非整格碰撞。</li>
 * <li>所有点位方块互不重叠，且都不落在核心区、门体、撤离区里（黑市箱与内部出口除外）。</li>
 * <li>庄园外墙把整张图围死：玩家走不到模板区域外的虚空。</li>
 * <li>全部方块都是整格方块，没有水、岩浆、沙、火、红石或方块实体，
 * 生成时不需要邻居更新，也不会出现流体/重力级联。</li>
 * </ul>
 */
public final class EchoManorLayout {
    public static final int WIDTH = 96;
    public static final int DEPTH = 96;
    public static final int HEIGHT = 14;
    /** 地面方块层（玩家站在这格上面）。 */
    public static final int GROUND_Y = 3;
    /** 脚层：所有点位标记方块所在的 y。 */
    public static final int FEET_Y = 4;

    private EchoManorLayout() { }

    /** 方块种类；{@link #AIR} 之外全部是整格方块。 */
    public enum Kind {
        AIR, STONE, DIRT, GRASS, PATH, FLOOR, WALL, GATE, PILLAR, GLASS, ROOF, ROOF_DARK, LAMP,
        LOG, LEAVES, CONTAINER, TABLE, COUNTER, BOOKSHELF, ANVIL, SMITHING, CRATE, DARKSTONE,
        MARKER_CRYSTAL, MARKER_BEACON, MARKER_ALTAR, MARKER_CRATE, MARKER_BLACK_CRATE,
        MARKER_BLACK_NODE, MARKER_BLACK_DROPOFF, MARKER_BROKER, MARKER_FREE_EXIT, PANEL;

        private static final Kind[] VALUES = values();

        public static Kind of(int ordinal) { return VALUES[ordinal]; }

        public boolean solid() { return this != AIR; }

        public boolean marker() { return name().startsWith("MARKER_") || this == PANEL; }
    }

    /** 脚层点位（id + 局部方块坐标）。 */
    public record Point(String id, int x, int z) { }
    /** 出生点（局部方块坐标 + 朝向）。 */
    public record SpawnPoint(int x, int z, float yaw) { }
    public record Beacon(String id, int x, int z, String region, int minPlayers) { }
    public record Node(String id, int x, int z, int minPlayers) { }
    public record Crate(String id, int x, int z, String quality, boolean repeatable) { }
    /** 核心入口：控制面板坐标（嵌在墙里）+ 门体半开区间。 */
    public record Door(String id, int panelX, int panelZ, Box volume) { }
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int volume() { return (maxX - minX) * (maxY - minY) * (maxZ - minZ); }
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }
    }
    /** 摆放在脚层的标记方块。 */
    public record Marker(Kind kind, String id, int x, int y, int z) { }

    /**
     * 完整布局。{@code cells} 按 x→y→z 排列（{@link #index}），空气也占位，
     * 这样生成器可以整块覆盖、不会残留旧地形。
     */
    public record Layout(byte[] cells, List<SpawnPoint> carriers, List<SpawnPoint> interceptors,
                         List<SpawnPoint> brokers, List<Beacon> beacons, List<Node> crystals,
                         List<Point> altars, List<Crate> crates, List<Point> blackNodes,
                         List<Point> blackDropoffs, Point brokerExit, Point freeControl, SpawnPoint freeSafe,
                         List<Door> doors, Box coreArea, Point evacControl, Box evacDoor, Box evacZone,
                         SpawnPoint evacSafe, Point dropAnchor, SpawnPoint spectator, List<Marker> markers) {

        public static int index(int x, int y, int z) { return (y * WIDTH + x) * DEPTH + z; }

        public Kind at(int x, int y, int z) {
            if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) return Kind.AIR;
            return Kind.of(cells[index(x, y, z)] & 0xFF);
        }

        public boolean solid(int x, int y, int z) { return at(x, y, z).solid(); }
    }

    // ==================== 构建 ====================

    public static Layout build() {
        Builder b = new Builder();
        b.terrain();
        b.estateWall();
        b.paving();
        b.manor();
        b.vault();
        b.wings();
        b.porch();
        b.evacuationGatehouse();
        b.southArch();
        b.yard();
        b.gardens();
        b.windows();
        b.furniture();
        b.lamps();
        return b.finish();
    }

    private static final class Builder {
        private final byte[] cells = new byte[WIDTH * HEIGHT * DEPTH];
        private final List<SpawnPoint> carriers = new ArrayList<>();
        private final List<SpawnPoint> interceptors = new ArrayList<>();
        private final List<SpawnPoint> brokers = new ArrayList<>();
        private final List<Beacon> beacons = new ArrayList<>();
        private final List<Node> crystals = new ArrayList<>();
        private final List<Point> altars = new ArrayList<>();
        private final List<Crate> crates = new ArrayList<>();
        private final List<Point> blackNodes = new ArrayList<>();
        private final List<Point> blackDropoffs = new ArrayList<>();
        private final List<Door> doors = new ArrayList<>();
        private final List<Marker> markers = new ArrayList<>();

        private void put(int x, int y, int z, Kind kind) {
            if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
                throw new IllegalArgumentException("布局越界: " + x + "," + y + "," + z);
            }
            cells[Layout.index(x, y, z)] = (byte) kind.ordinal();
        }

        private void fill(int x0, int x1, int y0, int y1, int z0, int z1, Kind kind) {
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) put(x, y, z, kind);
        }

        private void clear(int x0, int x1, int y0, int y1, int z0, int z1) {
            fill(x0, x1, y0, y1, z0, z1, Kind.AIR);
        }

        private Kind at(int x, int y, int z) {
            if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) return Kind.AIR;
            return Kind.of(cells[Layout.index(x, y, z)] & 0xFF);
        }

        private void terrain() {
            fill(0, WIDTH - 1, 0, 2, 0, DEPTH - 1, Kind.STONE);
            fill(0, WIDTH - 1, GROUND_Y, GROUND_Y, 0, DEPTH - 1, Kind.GRASS);
        }

        /** 2 厚 4 高的庄园外墙 + 四座角楼：把整张图围死，走不到模板外的虚空。 */
        private void estateWall() {
            fill(2, 3, 4, 7, 2, 93, Kind.WALL);
            fill(92, 93, 4, 7, 2, 93, Kind.WALL);
            fill(2, 93, 4, 7, 2, 3, Kind.WALL);
            fill(2, 93, 4, 7, 92, 93, Kind.WALL);
            for (int[] corner : new int[][]{{2, 2}, {88, 2}, {2, 88}, {88, 88}}) {
                fill(corner[0], corner[0] + 5, 4, 10, corner[1], corner[1] + 5, Kind.WALL);
                fill(corner[0] + 1, corner[0] + 4, 11, 11, corner[1] + 1, corner[1] + 4, Kind.ROOF);
            }
        }

        private void paving() {
            fill(30, 63, GROUND_Y, GROUND_Y, 58, 79, Kind.PATH);   // 庭院
            fill(44, 53, GROUND_Y, GROUND_Y, 57, 91, Kind.PATH);   // 主道
        }

        /** 主楼：外墙 5 高、天花 y=9、两级屋顶、南面 4 宽门洞。 */
        private void manor() {
            fill(26, 69, GROUND_Y, GROUND_Y, 26, 57, Kind.FLOOR);
            fill(26, 26, 4, 8, 26, 57, Kind.WALL);
            fill(69, 69, 4, 8, 26, 57, Kind.WALL);
            fill(26, 69, 4, 8, 26, 26, Kind.WALL);
            fill(26, 69, 4, 8, 57, 57, Kind.WALL);
            clear(48, 51, 4, 6, 57, 57);
            fill(26, 69, 9, 9, 26, 57, Kind.ROOF);
            fill(28, 67, 10, 10, 28, 55, Kind.ROOF);
            fill(32, 63, 11, 11, 32, 51, Kind.ROOF_DARK);
        }

        /** 金库：只有东西两个门洞，关上门就是封闭核心区。 */
        private void vault() {
            fill(42, 42, 4, 8, 29, 38, Kind.WALL);
            fill(57, 57, 4, 8, 29, 38, Kind.WALL);
            fill(42, 57, 4, 8, 29, 29, Kind.WALL);
            fill(42, 57, 4, 8, 38, 38, Kind.WALL);
            fill(42, 42, 4, 6, 33, 35, Kind.GATE);
            fill(57, 57, 4, 6, 33, 35, Kind.GATE);
            put(42, 5, 32, Kind.PANEL);
            put(57, 5, 32, Kind.PANEL);
            put(44, 4, 30, Kind.DARKSTONE);
            put(56, 4, 37, Kind.DARKSTONE);
            put(44, 4, 37, Kind.DARKSTONE);
            put(56, 4, 30, Kind.DARKSTONE);
        }

        /** 两翼：西侧工坊/厨房/餐厅，东侧仓库/藏书室/画廊，中间三格走廊。 */
        private void wings() {
            fill(38, 38, 4, 8, 27, 56, Kind.WALL);
            for (int z : new int[]{32, 42, 52}) clear(38, 38, 4, 6, z, z + 1);
            for (int z : new int[]{38, 47}) {
                fill(27, 37, 4, 8, z, z, Kind.WALL);
                clear(31, 32, 4, 6, z, z);
            }
            fill(61, 61, 4, 8, 27, 56, Kind.WALL);
            for (int z : new int[]{32, 42, 52}) clear(61, 61, 4, 6, z, z + 1);
            for (int z : new int[]{38, 47}) {
                fill(62, 68, 4, 8, z, z, Kind.WALL);
                clear(65, 66, 4, 6, z, z);
            }
            for (int[] pillar : new int[][]{{42, 44}, {57, 44}, {42, 52}, {57, 52}})
                fill(pillar[0], pillar[0], 4, 8, pillar[1], pillar[1], Kind.PILLAR);
        }

        private void porch() {
            for (int[] pillar : new int[][]{{44, 58}, {55, 58}, {44, 61}, {55, 61}})
                fill(pillar[0], pillar[0], 4, 7, pillar[1], pillar[1], Kind.PILLAR);
            fill(44, 55, 8, 8, 58, 61, Kind.ROOF);
            fill(44, 55, GROUND_Y, GROUND_Y, 58, 61, Kind.PATH);
        }

        /** 撤离门楼：门朝西，门后是封闭检测区；控制面板嵌在门北侧墙里。 */
        private void evacuationGatehouse() {
            fill(68, 68, 4, 8, 66, 78, Kind.WALL);
            fill(84, 84, 4, 8, 66, 78, Kind.WALL);
            fill(68, 84, 4, 8, 66, 66, Kind.WALL);
            fill(68, 84, 4, 8, 78, 78, Kind.WALL);
            fill(68, 68, 4, 6, 70, 72, Kind.GATE);
            put(68, 5, 69, Kind.PANEL);
            fill(68, 84, 9, 9, 66, 78, Kind.ROOF);
            fill(69, 83, GROUND_Y, GROUND_Y, 67, 77, Kind.FLOOR);
        }

        private void southArch() {
            fill(44, 44, 4, 7, 88, 88, Kind.PILLAR);
            fill(53, 53, 4, 7, 88, 88, Kind.PILLAR);
            fill(44, 53, 8, 8, 88, 88, Kind.ROOF);
        }

        /** 北院集装箱货场（追猎者出生点在这片空地上）。 */
        private void yard() {
            for (int[] box : new int[][]{{72, 6}, {82, 6}, {72, 15}}) {
                fill(box[0], box[0] + 5, 4, 6, box[1], box[1] + 2, Kind.CONTAINER);
                fill(box[0], box[0] + 5, 7, 7, box[1], box[1] + 2, Kind.ROOF_DARK);
            }
        }

        private void gardens() {
            for (int[] tree : new int[][]{{8, 10}, {18, 16}, {86, 10}, {86, 34}, {10, 86}, {86, 86}, {20, 46}, {75, 46}}) {
                fill(tree[0], tree[0], 4, 6, tree[1], tree[1], Kind.LOG);
                fill(tree[0] - 1, tree[0] + 1, 7, 8, tree[1] - 1, tree[1] + 1, Kind.LEAVES);
                clear(tree[0] - 1, tree[0] + 1, 9, 9, tree[1] - 1, tree[1] + 1);
            }
        }

        private void windows() {
            for (int z : new int[]{30, 34, 38, 42, 46, 50, 54}) {
                put(26, 6, z, Kind.GLASS);
                put(69, 6, z, Kind.GLASS);
            }
            for (int x : new int[]{30, 34, 38, 42, 58, 62, 66}) {
                put(x, 6, 26, Kind.GLASS);
                put(x, 6, 57, Kind.GLASS);
            }
        }

        private void furniture() {
            for (int z = 39; z <= 46; z++) put(68, 4, z, Kind.BOOKSHELF);
            for (int x = 30; x <= 34; x++) put(x, 4, 50, Kind.TABLE);
            for (int x = 27; x <= 29; x++) put(x, 4, 39, Kind.COUNTER);
            put(28, 4, 28, Kind.ANVIL);
            put(28, 4, 30, Kind.ANVIL);
            put(28, 4, 33, Kind.SMITHING);
            put(28, 4, 35, Kind.SMITHING);
            for (int z : new int[]{28, 30, 32}) put(67, 4, z, Kind.CRATE);
            put(62, 4, 50, Kind.PILLAR);
            put(62, 4, 54, Kind.PILLAR);
        }

        private void lamps() {
            for (int[] lamp : new int[][]{{46, 42}, {53, 42}, {46, 48}, {53, 48}, {46, 54}, {53, 54}})
                put(lamp[0], 8, lamp[1], Kind.LAMP);
            for (int[] lamp : new int[][]{{30, 26}, {65, 26}, {26, 40}, {69, 40}, {26, 50}, {69, 50}, {44, 57}, {55, 57}})
                put(lamp[0], 7, lamp[1], Kind.LAMP);
        }

        // ==================== 点位 ====================

        private void marker(Kind kind, String id, int x, int y, int z) {
            if (at(x, y, z) != Kind.AIR) throw new IllegalArgumentException("标记点位被建筑占用: " + id + " " + x + "," + y + "," + z);
            put(x, y, z, kind);
            markers.add(new Marker(kind, id, x, y, z));
        }

        private Layout finish() {
            // 出生点：南庭院托运者、北院追猎者、东西花园投机客
            for (int[] p : new int[][]{{32, 70}, {38, 72}, {44, 73}, {50, 73}, {56, 72}, {62, 70}, {44, 66}, {52, 66},
                    {32, 76}, {38, 78}, {44, 79}, {50, 79}, {56, 78}, {62, 76}, {36, 66}, {60, 66},
                    {32, 82}, {38, 84}, {44, 85}, {50, 85}, {56, 84}})
                carriers.add(new SpawnPoint(p[0], p[1], 180.0F));
            for (int[] p : new int[][]{{30, 14}, {66, 14}, {38, 14}, {58, 14}, {44, 14}, {50, 14}}) interceptors.add(new SpawnPoint(p[0], p[1], 0.0F));
            for (int[] p : new int[][]{{12, 50}, {84, 50}, {12, 54}, {84, 54}, {12, 58}}) brokers.add(new SpawnPoint(p[0], p[1], 90.0F));

            beacons.add(new Beacon("outer_a", 20, 66, "open", 6));
            beacons.add(new Beacon("core", 49, 31, "core", 6));
            beacons.add(new Beacon("outer_b", 77, 60, "open", 8));
            for (Beacon beacon : beacons) marker(Kind.MARKER_BEACON, "beacon/" + beacon.id(), beacon.x(), FEET_Y, beacon.z());

            crystals.add(new Node("c1", 14, 20, 6));
            crystals.add(new Node("c2", 14, 72, 6));
            crystals.add(new Node("c3", 83, 20, 6));
            crystals.add(new Node("c4", 86, 74, 6));
            crystals.add(new Node("c5", 32, 12, 8));
            crystals.add(new Node("c6", 64, 12, 8));
            for (Node node : crystals) marker(Kind.MARKER_CRYSTAL, "crystal/" + node.id(), node.x(), FEET_Y, node.z());

            altars.add(new Point("a1", 12, 40));
            altars.add(new Point("a2", 84, 40));
            altars.add(new Point("a3", 48, 68));
            for (Point altar : altars) marker(Kind.MARKER_ALTAR, "altar/" + altar.id(), altar.x(), FEET_Y, altar.z());

            int[][] commonCrates = {{8, 30}, {8, 60}, {88, 30}, {88, 60}, {22, 80}, {75, 80}, {30, 66}, {60, 62}, {30, 16}, {66, 18}};
            for (int n = 0; n < commonCrates.length; n++) {
                String id = "s" + (n + 1);
                crates.add(new Crate(id, commonCrates[n][0], commonCrates[n][1], "COMMON", true));
                marker(Kind.MARKER_CRATE, "crate/" + id, commonCrates[n][0], FEET_Y, commonCrates[n][1]);
            }
            int[][] blackMarket = {{45, 35}, {53, 35}};
            for (int n = 0; n < blackMarket.length; n++) {
                String id = "b" + (n + 1);
                crates.add(new Crate(id, blackMarket[n][0], blackMarket[n][1], "BLACK_MARKET", false));
                marker(Kind.MARKER_BLACK_CRATE, "crate/" + id, blackMarket[n][0], FEET_Y, blackMarket[n][1]);
            }

            blackNodes.add(new Point("black_w", 8, 12));
            blackNodes.add(new Point("black_e", 88, 84));
            for (Point node : blackNodes) marker(Kind.MARKER_BLACK_NODE, "black/" + node.id(), node.x(), FEET_Y, node.z());
            blackDropoffs.add(new Point("drop_w", 16, 84));
            blackDropoffs.add(new Point("drop_e", 80, 12));
            for (Point drop : blackDropoffs) marker(Kind.MARKER_BLACK_DROPOFF, "drop/" + drop.id(), drop.x(), FEET_Y, drop.z());

            Point brokerExit = new Point("broker_exit", 48, 90);
            marker(Kind.MARKER_BROKER, "broker/" + brokerExit.id(), brokerExit.x(), FEET_Y, brokerExit.z());
            Point freeControl = new Point("service_exit", 49, 35);
            marker(Kind.MARKER_FREE_EXIT, "free/" + freeControl.id(), freeControl.x(), FEET_Y, freeControl.z());

            // 控制面板已经作为墙块存在，这里只登记坐标并核对它确实在墙里。
            doors.add(new Door("west", 42, 32, new Box(42, 4, 33, 43, 7, 36)));
            doors.add(new Door("east", 57, 32, new Box(57, 4, 33, 58, 7, 36)));
            for (Door door : doors) {
                if (!at(door.panelX(), 5, door.panelZ()).solid())
                    throw new IllegalArgumentException("核心门控制面板不在墙里: " + door.id());
                markers.add(new Marker(Kind.PANEL, "core/" + door.id(), door.panelX(), 5, door.panelZ()));
            }
            if (!at(68, 5, 69).solid()) throw new IllegalArgumentException("撤离门控制面板不在墙里");
            markers.add(new Marker(Kind.PANEL, "evac/control", 68, 5, 69));

            return new Layout(cells.clone(), List.copyOf(carriers), List.copyOf(interceptors), List.copyOf(brokers),
                    List.copyOf(beacons), List.copyOf(crystals), List.copyOf(altars), List.copyOf(crates),
                    List.copyOf(blackNodes), List.copyOf(blackDropoffs), brokerExit, freeControl,
                    new SpawnPoint(49, 42, 180.0F), List.copyOf(doors), new Box(43, 4, 30, 57, 11, 38),
                    new Point("evac_control", 68, 69), new Box(68, 4, 70, 69, 7, 73),
                    new Box(70, 4, 68, 83, 8, 77), new SpawnPoint(67, 71, 270.0F),
                    new Point("exit_drop", 66, 74), new SpawnPoint(48, 60, 180.0F),
                    List.copyOf(markers));
        }
    }
}
