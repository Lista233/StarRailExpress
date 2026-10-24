package org.agmas.noellesroles.game.modes.echo.map.manor;

import java.util.ArrayList;
import java.util.List;

/**
 * 回声货运测试地图「回声枢纽」的纯几何布局：只描述方块种类与模式点位，不引用任何 Minecraft 类。
 *
 * <p>坐标系：局部坐标 x 0..199（西→东）、z 0..199（北→南）、y 0..21（下→上）。
 * 地面基准方块在 {@link #GROUND_Y}，玩家脚踩 {@link #FEET_Y}。
 *
 * <p>绘制顺序固定为
 * {@code 地基 → 外围假山 → 分区地面 → 台地 → 假山 → 道路 → 建筑 → 树木 → 点位}，
 * 后画的图层直接盖掉先画的图层，所以道路能切穿台地、建筑能压平地面，不会出现半格错位。
 *
 * <p>结构不变量（改动坐标时必须同时满足，{@code EchoManorLayoutTest} 会逐条验证）：
 * <ul>
 * <li><b>通路平面唯一</b>：所有点位、出生点、门体、撤离区都落在 {@link #FEET_Y} 基准平面。
 * 台地、假山只做视觉起伏，不承担连通性，因此 {@code canJump=false} 时整张图依然走得通。</li>
 * <li>台地一律 1–2 格高差、整格方块；不放台阶、半砖、栅栏、梯子，避免路线准入退化成人工复核。</li>
 * <li>金库（核心区）只有东西两个门洞，门体关闭时内部完全封闭；门体是整格铁块。</li>
 * <li>所有点位方块互不重叠，且都不落在核心区、门体、撤离区里（黑市箱与内部出口除外）。</li>
 * <li>外围环形假山把整张图围死：玩家走不到模板区域外的虚空。</li>
 * <li>全部方块都是整格方块，没有水、岩浆、沙、砂砾、火、红石或方块实体，
 * 生成时不需要邻居更新，也不会出现流体/重力级联。</li>
 * <li>道路用整数坐标折线 + 圆形印章铺设，转角处自然重叠，不会留下 1 格缝隙。</li>
 * </ul>
 */
public final class EchoManorLayout {
    public static final int WIDTH = 200;
    public static final int DEPTH = 200;
    public static final int HEIGHT = 22;
    /** 地面方块层（玩家站在这格上面）。 */
    public static final int GROUND_Y = 3;
    /** 脚层：所有点位标记方块所在的 y。 */
    public static final int FEET_Y = 4;

    private EchoManorLayout() { }

    /** 方块种类；{@link #AIR} 之外全部是整格方块。 */
    public enum Kind {
        AIR, STONE, DIRT, GRASS, PATH, FLOOR, WALL, GATE, PILLAR, GLASS, ROOF, ROOF_DARK, LAMP,
        LOG, LEAVES, CONTAINER, TABLE, COUNTER, BOOKSHELF, CRATE, DARKSTONE,
        /** 假山岩体 / 苔藓面 / 工业硬地 / 金属平台 / 台地顶面 / 台地挡墙。 */
        ROCK, MOSS, CONCRETE, PLATE, TERRACE, TERRACE_WALL,
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
        b.baseTerrain();
        b.boundaryRidge();
        b.districtFloors();
        b.terraces();
        b.rockeries();
        b.roads();
        b.coreFortress();
        b.evacuationGatehouse();
        b.northYard();
        b.westWorks();
        b.eastMarket();
        b.southCamp();
        b.gardens();
        b.points();
        return b.finish();
    }

    private static final class Builder {
        private final EchoManorCanvas canvas = new EchoManorCanvas();
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
        private Point brokerExit;
        private Point freeControl;

        private Kind at(int x, int y, int z) { return canvas.at(x, y, z); }

        private void put(int x, int y, int z, Kind kind) { canvas.put(x, y, z, kind); }

        private void fill(int x0, int x1, int y0, int y1, int z0, int z1, Kind kind) {
            canvas.fill(x0, x1, y0, y1, z0, z1, kind);
        }

        private void clear(int x0, int x1, int y0, int y1, int z0, int z1) {
            canvas.clear(x0, x1, y0, y1, z0, z1);
        }

        // ---------- 地形 ----------

        /** 三层地基 + 草地表面：整张图的地面基准面，之后所有地形都在它上面做文章。 */
        private void baseTerrain() {
            fill(0, WIDTH - 1, 0, GROUND_Y - 1, 0, DEPTH - 1, Kind.STONE);
            fill(0, WIDTH - 1, GROUND_Y, GROUND_Y, 0, DEPTH - 1, Kind.GRASS);
        }

        /**
         * 外围环形假山：把整张图围死，走不到模板外的虚空，同时给地图一圈起伏的天际线。
         * 最外圈始终至少 6 格高，任何跳跃设置都翻不出去。
         */
        private void boundaryRidge() {
            for (int x = 0; x < WIDTH; x++) {
                for (int z = 0; z < DEPTH; z++) {
                    int edge = Math.min(Math.min(x, z), Math.min(WIDTH - 1 - x, DEPTH - 1 - z));
                    if (edge >= 5) continue;
                    int peak = 6 + Math.floorMod(x * 31 + z * 57, 5) + (edge < 2 ? 3 : 0);
                    for (int y = GROUND_Y + 1; y <= GROUND_Y + peak; y++) {
                        Kind body = Math.floorMod(x * 17 + y * 7 + z * 23, 9) < 3 ? Kind.MOSS : Kind.ROCK;
                        put(x, y, z, body);
                    }
                    put(x, GROUND_Y, z, Kind.ROCK);
                }
            }
        }

        /** 每个分区换一种地面材质，让 200×200 一眼能分出几个区。 */
        private void districtFloors() {
            canvas.flatten(30, 170, 8, 54, Kind.CONCRETE);     // 北院货场
            canvas.flatten(14, 58, 64, 148, Kind.PLATE);       // 西侧工业区
            canvas.flatten(142, 186, 64, 148, Kind.FLOOR);     // 东侧仓储市场
            canvas.flatten(40, 146, 150, 192, Kind.GRASS);     // 南侧托运者营地
            canvas.flatten(84, 116, 58, 86, Kind.FLOOR);       // 核心枢纽
        }

        /**
         * 台地：四个角各抬 1 格，作为货场月台与观景台。
         * 台地不承担连通性——所有点位都在基准平面，跳不上去也不影响开局路线。
         */
        private void terraces() {
            terrace(24, 38, 26, 40);
            terrace(162, 176, 26, 40);
            terrace(22, 30, 182, 192);
            terrace(168, 178, 182, 192);
        }

        private void terrace(int x0, int x1, int z0, int z1) {
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) canvas.raise(x, z, 1, Kind.TERRACE, Kind.TERRACE_WALL);
        }

        /**
         * 假山群：只放在分区之间的空白带上，用来填补空旷、同时把几个区隔开。
         * 会挡住人的石堆都由后面画的道路切出通道，所以不会把地图割裂。
         */
        private void rockeries() {
            canvas.rockery(100, 44, 8, 9, 1);     // 核心北侧，主道从西南绕过去
            canvas.rockery(24, 56, 6, 6, 2);      // 西北角
            canvas.rockery(178, 54, 6, 6, 3);     // 东北角
            canvas.rockery(60, 120, 9, 8, 4);     // 西区中部石林
            canvas.rockery(134, 118, 8, 8, 5);    // 东区中部石林
            canvas.rockery(116, 148, 6, 6, 6);    // 南营东北
            canvas.rockery(24, 168, 6, 6, 7);     // 西南角
            canvas.rockery(180, 168, 6, 6, 8);    // 东南角
            canvas.rockery(70, 62, 5, 5, 9);      // 核心西北
            canvas.rockery(132, 62, 5, 5, 10);    // 核心东北
            canvas.rockery(100, 12, 7, 5, 11);    // 北院正中
            canvas.rockery(16, 20, 5, 5, 12);     // 北院西侧空地
            canvas.rockery(184, 20, 5, 5, 13);    // 北院东侧空地
            canvas.rockery(14, 100, 4, 4, 14);    // 西侧窄带
            canvas.rockery(188, 104, 4, 4, 15);   // 东侧窄带
        }

        /** 道路：折线 + 圆形印章，没有一条是笔直的长线。 */
        private void roads() {
            canvas.disc(100, 100, 20, Kind.PATH);          // 中央枢纽广场
            // 主脊：南营 → 广场 → 核心南门
            canvas.road(new int[][]{{100, 172}, {96, 150}, {102, 132}, {98, 116}, {100, 104}}, 4, Kind.PATH);
            canvas.road(new int[][]{{100, 104}, {97, 96}, {100, 88}}, 4, Kind.PATH);
            // 核心北门 → 北院货场（从假山西侧绕过去）
            canvas.road(new int[][]{{100, 56}, {94, 50}, {84, 44}, {74, 38}, {64, 32}, {56, 26}}, 3, Kind.PATH);
            // 西侧支线：广场 → 工业区 → 西南
            canvas.road(new int[][]{{100, 98}, {86, 100}, {72, 104}, {58, 108}, {46, 114}, {36, 122}, {30, 134}, {28, 146}},
                    3, Kind.PATH);
            // 东侧支线：广场 → 市场 → 东南
            canvas.road(new int[][]{{100, 98}, {114, 100}, {128, 104}, {142, 106}, {154, 110}, {166, 118}, {172, 130}},
                    3, Kind.PATH);
            // 北院环线
            canvas.road(new int[][]{{56, 26}, {74, 20}, {94, 18}, {114, 20}, {132, 26}, {146, 34}}, 3, Kind.PATH);
            // 南侧环线：营地 → 撤离门楼西侧
            canvas.road(new int[][]{{100, 172}, {116, 178}, {132, 182}, {144, 176}, {148, 168}, {146, 164}},
                    3, Kind.PATH);
            // 西侧南北联络道（不是直线）
            canvas.road(new int[][]{{28, 146}, {26, 160}, {34, 172}, {48, 178}}, 3, Kind.PATH);
            // 东侧南北联络道
            canvas.road(new int[][]{{172, 130}, {178, 142}, {176, 152}, {168, 158}}, 3, Kind.PATH);
        }

        // ---------- 建筑 ----------

        /**
         * 核心枢纽：外墙 6 高的大厅，南北各一扇 8 宽大门；正中是只有东西两扇铁门的小金库。
         * 关门后金库完全封闭，开门才能拿到核心信标 / 黑市箱 / 免费出口。
         */
        private void coreFortress() {
            fill(84, 116, GROUND_Y, GROUND_Y, 58, 86, Kind.FLOOR);
            fill(84, 116, GROUND_Y, GROUND_Y, 58, 86, Kind.FLOOR);
            clear(85, 115, FEET_Y, 9, 59, 85);   // 先掏空大厅，防止假山/台地残留
            fill(84, 84, FEET_Y, 9, 58, 86, Kind.WALL);
            fill(116, 116, FEET_Y, 9, 58, 86, Kind.WALL);
            fill(84, 116, FEET_Y, 9, 58, 58, Kind.WALL);
            fill(84, 116, FEET_Y, 9, 86, 86, Kind.WALL);
            clear(96, 103, FEET_Y, 6, 58, 58);   // 北门
            clear(96, 103, FEET_Y, 6, 86, 86);   // 南门
            fill(84, 116, 10, 10, 58, 86, Kind.ROOF);
            fill(86, 114, 11, 11, 60, 84, Kind.ROOF_DARK);

            // 金库本体：只有东西两扇铁门
            fill(92, 92, FEET_Y, 8, 64, 80, Kind.WALL);
            fill(108, 108, FEET_Y, 8, 64, 80, Kind.WALL);
            fill(92, 108, FEET_Y, 8, 64, 64, Kind.WALL);
            fill(92, 108, FEET_Y, 8, 80, 80, Kind.WALL);
            clear(93, 107, FEET_Y, 8, 65, 79);   // 金库内部也先掏空
            fill(92, 92, FEET_Y, 6, 70, 72, Kind.GATE);
            fill(108, 108, FEET_Y, 6, 70, 72, Kind.GATE);
            fill(93, 107, 9, 9, 65, 79, Kind.ROOF_DARK);
            put(92, 5, 69, Kind.PANEL);
            put(108, 5, 69, Kind.PANEL);
            for (int[] corner : new int[][]{{94, 66}, {106, 66}, {94, 78}, {106, 78}}) {
                fill(corner[0], corner[0], FEET_Y, 7, corner[1], corner[1], Kind.PILLAR);
            }
            for (int z : new int[]{62, 68, 74, 82}) {
                put(84, 6, z, Kind.GLASS);
                put(116, 6, z, Kind.GLASS);
            }
            for (int x : new int[]{88, 94, 106, 112}) {
                put(x, 6, 58, Kind.GLASS);
                put(x, 6, 86, Kind.GLASS);
            }
            // 大厅里的灯柱，撑起室内层次
            for (int[] lamp : new int[][]{{88, 64}, {112, 64}, {88, 80}, {112, 80}}) {
                fill(lamp[0], lamp[0], FEET_Y, 8, lamp[1], lamp[1], Kind.PILLAR);
                put(lamp[0], 9, lamp[1], Kind.LAMP);
            }
        }

        /** 撤离门楼：门朝西，门后是封闭检测区；控制面板嵌在门北侧墙里。 */
        private void evacuationGatehouse() {
            fill(150, 182, GROUND_Y, GROUND_Y, 152, 178, Kind.FLOOR);
            clear(151, 181, FEET_Y, 8, 153, 177);   // 先掏空门楼，防止假山残留
            fill(150, 150, FEET_Y, 8, 152, 178, Kind.WALL);
            fill(182, 182, FEET_Y, 8, 152, 178, Kind.WALL);
            fill(150, 182, FEET_Y, 8, 152, 152, Kind.WALL);
            fill(150, 182, FEET_Y, 8, 178, 178, Kind.WALL);
            fill(150, 150, FEET_Y, 6, 163, 165, Kind.GATE);
            put(150, 5, 162, Kind.PANEL);
            fill(150, 182, 9, 9, 152, 178, Kind.ROOF);
            fill(152, 180, 10, 10, 154, 176, Kind.ROOF_DARK);
            for (int z : new int[]{156, 160, 168, 172, 176}) put(182, 6, z, Kind.GLASS);
            for (int[] lamp : new int[][]{{156, 156}, {176, 156}, {156, 174}, {176, 174}}) {
                fill(lamp[0], lamp[0], FEET_Y, 7, lamp[1], lamp[1], Kind.PILLAR);
                put(lamp[0], 8, lamp[1], Kind.LAMP);
            }
        }

        /** 北院货场：集装箱堆、龙门吊、货运棚。 */
        private void northYard() {
            for (int[] box : new int[][]{{44, 16}, {60, 16}, {76, 16}, {44, 30}, {136, 16}, {152, 16}, {136, 30},
                    {152, 30}, {50, 42}, {146, 42}}) {
                canvas.container(box[0], box[1], 6, 3, 2);
            }
            canvas.container(88, 12, 8, 3, 3);
            canvas.container(104, 12, 8, 3, 3);
            // 龙门吊：四根立柱 + 顶梁，横跨货场东侧
            for (int[] post : new int[][]{{118, 34}, {142, 34}, {118, 46}, {142, 46}}) {
                fill(post[0], post[0], FEET_Y, 9, post[1], post[1], Kind.PILLAR);
            }
            fill(118, 142, 10, 10, 34, 34, Kind.PLATE);
            fill(118, 142, 10, 10, 46, 46, Kind.PLATE);
            fill(118, 118, 10, 10, 34, 46, Kind.PLATE);
            fill(142, 142, 10, 10, 34, 46, Kind.PLATE);
            put(130, 10, 40, Kind.LAMP);
            // 货运棚
            fill(58, 82, GROUND_Y, GROUND_Y, 40, 52, Kind.FLOOR);
            fill(58, 58, FEET_Y, 8, 40, 52, Kind.WALL);
            fill(82, 82, FEET_Y, 8, 40, 52, Kind.WALL);
            fill(58, 82, FEET_Y, 8, 40, 40, Kind.WALL);
            fill(58, 82, FEET_Y, 8, 52, 52, Kind.WALL);
            clear(64, 69, FEET_Y, 6, 52, 52);
            fill(58, 82, 9, 9, 40, 52, Kind.ROOF);
            fill(60, 80, 10, 10, 42, 50, Kind.ROOF_DARK);
        }

        /** 西侧工业区：厂房、料仓、烟囱、管线。 */
        private void westWorks() {
            canvas.house(20, 40, 66, 82, 8, Kind.PLATE);
            canvas.house(20, 38, 88, 104, 8, Kind.PLATE);
            canvas.house(22, 44, 108, 122, 8, Kind.PLATE);
            canvas.house(18, 34, 132, 148, 7, Kind.PLATE);
            // 烟囱与料仓（整格实心，不用流体）
            fill(48, 48, FEET_Y, 13, 70, 70, Kind.PILLAR);
            fill(52, 52, FEET_Y, 11, 74, 74, Kind.PILLAR);
            fill(48, 48, 14, 14, 70, 70, Kind.LAMP);
            fill(50, 54, GROUND_Y, GROUND_Y, 90, 96, Kind.PLATE);
            fill(50, 54, FEET_Y, FEET_Y + 2, 90, 96, Kind.DARKSTONE);
            for (int x = 20; x <= 44; x += 8) fill(x, x, FEET_Y, FEET_Y, 106, 108, Kind.PLATE);
            for (int[] lamp : new int[][]{{14, 96}, {46, 90}, {14, 136}, {40, 140}}) {
                canvas.lamp(lamp[0], lamp[1], 4);
            }
        }

        /** 东侧仓储市场：仓库、摊位、货堆。 */
        private void eastMarket() {
            canvas.house(156, 178, 66, 84, 8, Kind.FLOOR);
            canvas.house(158, 176, 90, 106, 8, Kind.FLOOR);
            canvas.house(154, 174, 112, 126, 8, Kind.FLOOR);
            canvas.house(158, 176, 134, 148, 7, Kind.FLOOR);
            // 摊位：两排台面 + 顶棚
            for (int z : new int[]{96, 126}) {
                for (int x = 146; x <= 152; x += 3) {
                    fill(x, x + 1, FEET_Y, FEET_Y, z, z + 3, Kind.TABLE);
                    fill(x, x + 1, FEET_Y + 2, FEET_Y + 2, z, z + 3, Kind.ROOF);
                    put(x, FEET_Y + 1, z, Kind.LOG);
                    put(x + 1, FEET_Y + 1, z + 3, Kind.LOG);
                }
            }
            for (int[] lamp : new int[][]{{144, 90}, {144, 132}, {186, 96}, {186, 120}}) {
                canvas.lamp(lamp[0], lamp[1], 4);
            }
        }

        /** 南侧托运者营地：帐篷、营地灯。 */
        private void southCamp() {
            for (int[] tent : new int[][]{{46, 154}, {78, 154}, {114, 154}, {136, 156}, {56, 178}, {100, 182},
                    {130, 182}}) {
                fill(tent[0], tent[0] + 3, FEET_Y, FEET_Y + 2, tent[1], tent[1] + 3, Kind.WALL);
                fill(tent[0] - 1, tent[0] + 4, FEET_Y + 3, FEET_Y + 3, tent[1] - 1, tent[1] + 4, Kind.ROOF);
            }
            for (int x = 44; x <= 144; x += 12) canvas.lamp(x, 148, 4);
        }

        /** 行道树与零星景观，只放在没有点位、没有建筑的空白带上。 */
        private void gardens() {
            for (int[] tree : new int[][]{{12, 72}, {12, 100}, {12, 128}, {188, 72}, {188, 100}, {188, 128},
                    {44, 90}, {156, 90}, {60, 148}, {140, 148}, {70, 158}, {130, 158}, {100, 146},
                    {70, 88}, {130, 88}, {50, 62}, {150, 62}, {100, 62}}) {
                canvas.tree(tree[0], tree[1], 4);
            }
        }

        // ---------- 点位 ----------

        /** 标记方块：必须踩在基准平面的实心地面上，脚与头两格为空。 */
        private void marker(Kind kind, String id, int x, int z) {
            if (!at(x, GROUND_Y, z).solid())
                throw new IllegalArgumentException("标记点位下方没有地面: " + id + " " + x + "," + z);
            if (at(x, FEET_Y, z) != Kind.AIR || at(x, FEET_Y + 1, z) != Kind.AIR)
                throw new IllegalArgumentException("标记点位被建筑/地形占用: " + id + " " + x + "," + z);
            put(x, FEET_Y, z, kind);
            markers.add(new Marker(kind, id, x, FEET_Y, z));
        }

        private void spawnCheck(String what, int x, int z) {
            if (!at(x, GROUND_Y, z).solid() || at(x, FEET_Y, z) != Kind.AIR || at(x, FEET_Y + 1, z) != Kind.AIR)
                throw new IllegalArgumentException(what + "出生点不合法: " + x + "," + z);
        }

        private void carrier(int x, int z) {
            spawnCheck("托运者", x, z);
            carriers.add(new SpawnPoint(x, z, 180.0F));
        }

        private void interceptor(int x, int z) {
            spawnCheck("截流者", x, z);
            interceptors.add(new SpawnPoint(x, z, 0.0F));
        }

        private void broker(int x, int z) {
            spawnCheck("投机客", x, z);
            brokers.add(new SpawnPoint(x, z, 90.0F));
        }

        private void points() {
            // 出生点：南营托运者、北院截流者、东西两侧与南侧投机客
            int[][] carrierSpots = {{52, 156}, {64, 160}, {76, 164}, {88, 168}, {100, 172}, {112, 168},
                    {124, 164}, {136, 160}, {148, 156}, {52, 172}, {64, 176}, {76, 180}, {88, 184},
                    {100, 188}, {112, 184}, {124, 180}, {136, 176}, {148, 172}, {70, 168}, {90, 176},
                    {120, 176}};
            for (int[] p : carrierSpots) carrier(p[0], p[1]);
            int[][] interceptorSpots = {{56, 28}, {72, 22}, {88, 18}, {112, 18}, {128, 22}, {144, 28}};
            for (int[] p : interceptorSpots) interceptor(p[0], p[1]);
            int[][] brokerSpots = {{20, 128}, {44, 136}, {180, 128}, {180, 140}, {100, 144}};
            for (int[] p : brokerSpots) broker(p[0], p[1]);

            beacons.add(new Beacon("outer_a", 40, 96, "open", 6));
            beacons.add(new Beacon("core", 100, 70, "core", 6));
            beacons.add(new Beacon("outer_b", 152, 120, "open", 8));
            for (Beacon beacon : beacons) marker(Kind.MARKER_BEACON, "beacon/" + beacon.id(), beacon.x(), beacon.z());

            crystals.add(new Node("c1", 26, 44, 6));
            crystals.add(new Node("c2", 26, 152, 6));
            crystals.add(new Node("c3", 174, 44, 6));
            crystals.add(new Node("c4", 148, 140, 6));
            crystals.add(new Node("c5", 60, 24, 8));
            crystals.add(new Node("c6", 140, 24, 8));
            for (Node node : crystals) marker(Kind.MARKER_CRYSTAL, "crystal/" + node.id(), node.x(), node.z());

            altars.add(new Point("a1", 62, 92));
            altars.add(new Point("a2", 138, 92));
            altars.add(new Point("a3", 78, 136));
            for (Point altar : altars) marker(Kind.MARKER_ALTAR, "altar/" + altar.id(), altar.x(), altar.z());

            int[][] commonCrates = {{30, 84}, {30, 128}, {186, 84}, {188, 116}, {70, 140}, {130, 140},
                    {60, 60}, {140, 60}, {80, 160}, {120, 160}};
            for (int n = 0; n < commonCrates.length; n++) {
                String id = "s" + (n + 1);
                crates.add(new Crate(id, commonCrates[n][0], commonCrates[n][1], "COMMON", true));
                marker(Kind.MARKER_CRATE, "crate/" + id, commonCrates[n][0], commonCrates[n][1]);
            }
            int[][] blackMarket = {{95, 78}, {105, 78}};
            for (int n = 0; n < blackMarket.length; n++) {
                String id = "b" + (n + 1);
                crates.add(new Crate(id, blackMarket[n][0], blackMarket[n][1], "BLACK_MARKET", false));
                marker(Kind.MARKER_BLACK_CRATE, "crate/" + id, blackMarket[n][0], blackMarket[n][1]);
            }

            blackNodes.add(new Point("black_w", 16, 110));
            blackNodes.add(new Point("black_e", 180, 110));
            for (Point node : blackNodes) marker(Kind.MARKER_BLACK_NODE, "black/" + node.id(), node.x(), node.z());
            blackDropoffs.add(new Point("drop_w", 44, 166));
            blackDropoffs.add(new Point("drop_e", 156, 60));
            for (Point drop : blackDropoffs) marker(Kind.MARKER_BLACK_DROPOFF, "drop/" + drop.id(), drop.x(), drop.z());

            brokerExit = new Point("broker_exit", 100, 180);
            marker(Kind.MARKER_BROKER, "broker/" + brokerExit.id(), brokerExit.x(), brokerExit.z());
            freeControl = new Point("service_exit", 100, 68);
            marker(Kind.MARKER_FREE_EXIT, "free/" + freeControl.id(), freeControl.x(), freeControl.z());

            // 控制面板已经作为墙块存在，这里只登记坐标并核对它确实在墙里。
            doors.add(new Door("west", 92, 69, new Box(92, 4, 70, 93, 7, 73)));
            doors.add(new Door("east", 108, 69, new Box(108, 4, 70, 109, 7, 73)));
            for (Door door : doors) {
                if (!at(door.panelX(), 5, door.panelZ()).solid())
                    throw new IllegalArgumentException("核心门控制面板不在墙里: " + door.id());
                markers.add(new Marker(Kind.PANEL, "core/" + door.id(), door.panelX(), 5, door.panelZ()));
            }
            if (!at(150, 5, 162).solid()) throw new IllegalArgumentException("撤离门控制面板不在墙里");
            markers.add(new Marker(Kind.PANEL, "evac/control", 150, 5, 162));
        }

        private Layout finish() {
            return new Layout(canvas.snapshot(), List.copyOf(carriers), List.copyOf(interceptors), List.copyOf(brokers),
                    List.copyOf(beacons), List.copyOf(crystals), List.copyOf(altars), List.copyOf(crates),
                    List.copyOf(blackNodes), List.copyOf(blackDropoffs), brokerExit, freeControl,
                    new SpawnPoint(100, 92, 180.0F), List.copyOf(doors), new Box(93, 4, 65, 108, 9, 80),
                    new Point("evac_control", 150, 162), new Box(150, 4, 163, 151, 7, 166),
                    new Box(152, 4, 156, 178, 8, 176), new SpawnPoint(146, 163, 270.0F),
                    new Point("exit_drop", 146, 170), new SpawnPoint(100, 100, 180.0F),
                    List.copyOf(markers));
        }
    }
}
