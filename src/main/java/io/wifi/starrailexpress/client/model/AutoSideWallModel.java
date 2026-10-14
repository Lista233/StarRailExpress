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

package io.wifi.starrailexpress.client.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 给资源包里的「平面模型」<b>自动补一圈 1 像素厚的侧壁</b>的 {@link BakedModel} 包装。
 *
 * <p>
 * 背景：自定义列车物品的「模型地址」指向的模型 json 常常写成平面（一个 0 厚度的
 * {@code element}，或者两个相距 1 像素、朝相反方向的面），从侧面看就是两张互不相连的
 * 纸片。而「<b>不填</b>模型地址时走的默认模型（
 * {@link io.wifi.starrailexpress.client.render.item.CustomItemRenderer} 里的 {@code drawQuad}）
 * 是前后两层 + 四面侧壁的 1 像素厚实体 —— 于是同一个物品「有模型地址」和「没有模型地址」
 * 会看起来厚薄不一致。这里在<b>不改资源包</b>的前提下，把同样的厚度补给模型。
 *
 * <p>
 * 识别规则（保守，只动「一眼能看出是薄片」的东西）：
 * <ul>
 * <li>只处理<b>轴对齐的矩形面片</b>：某个轴上的跨度≈0（旋转过的面片、异形面一律不动）；</li>
 * <li>同一个矩形上、法线轴跨度 ≤ 1 像素的一组面片（0 厚度、两个相反朝向都算）视为一张薄片，
 * 按<b>中心对称</b>补到正好 1 像素厚：原跨度 1 像素时原样（侧壁正好落在两个面之间，与默认模型
 * 的 7.5 / 8.5 完全重合），原跨度 0 时每个方向各让半个像素；</li>
 * <li>跨度超过 1 像素的组视为「本来就是实心的元素」，原样保留，交给模型自己的几何。</li>
 * </ul>
 *
 * <p>
 * 侧壁的 UV 沿用该角在薄片上的 UV（厚度方向退化 = 整条边共用同一个纹理列/行），
 * 颜色沿高度/宽度方向自然过渡 —— 与默认模型的侧壁做法一致。
 * 只写了一个朝向的面（很多立体元素的侧面就长这样）<b>不处理</b>：那种情况分不清它是
 * 「单面纸片」还是「实心盒子的一张侧面」，补了会围着盒子的棱角多出 1/16 宽的碎片；
 * 而实心盒子的正反面组补出来的四面侧壁与它自己的侧面位置完全重合，会被签名挡掉、不会重复。
 * 与模型自带面<b>位置重合</b>的侧壁同样不会重复添加（否则半透明会叠两层、整体变暗）。
 *
 * <p>
 * 只包装「模型地址」解析出来的模型（见
 * {@link io.wifi.starrailexpress.client.render.item.CustomItemRenderer#resolveConfiguredModel}），
 * 因此不会影响其它物品 / 方块用的同名模型。
 *
 * <p>
 * 侧壁几何在<b>第一次被取用</b>时算一次并缓存（渲染线程上）；按模型 id 缓存包装实例，
 * 避免每帧重新分析（模型重载后实例变了会自动重建）。
 */
@Environment(EnvType.CLIENT)
public final class AutoSideWallModel implements BakedModel {

    /** 一张「薄片」的最大厚度：1 像素（模型空间 = 1/16）。更厚的一律当作实心元素，不动。 */
    private static final float THICKNESS = 1.0F / 16.0F;

    /** 浮点容差（模型空间 [0,1]；1e-4 相当于 1/1000 像素）。 */
    private static final float EPS = 1.0e-4F;

    /** 顶点签名 / 分组的量化精度，用来把 FaceBakery 的浮点误差归一。 */
    private static final float QUANT = 1.0e4F;

    /** 模型 id → 包装（模型重载后实例变了会用新的替掉旧的；只保留每个 id 一份，不会越积越多）。 */
    private static final Map<ResourceLocation, AutoSideWallModel> CACHE = new ConcurrentHashMap<>();

    /**
     * 包一层「自动补侧壁」。
     *
     * @param id    「模型地址」解析出来的模型 id（为 null 时不做缓存，直接返回原模型）
     * @param model 原烘焙模型
     * @return 包装后的模型（无需补侧壁的场景下仍然是包装实例，只是取用时原样返回）
     */
    public static BakedModel wrap(@Nullable ResourceLocation id, @Nullable BakedModel model) {
        if (model == null || model instanceof AutoSideWallModel || id == null) {
            return model;
        }
        AutoSideWallModel cached = CACHE.get(id);
        if (cached != null && cached.delegate == model) {
            return cached;
        }
        AutoSideWallModel created = new AutoSideWallModel(model);
        CACHE.put(id, created);
        return created;
    }

    /** 模型重载 / 配置变化后清空包装缓存。 */
    public static void clearCache() {
        CACHE.clear();
    }

    private final BakedModel delegate;

    /** 补出来的侧壁（惰性构建，渲染线程上算一次）。 */
    private List<BakedQuad> walls;
    private boolean wallsBuilt;

    /** 「原 quad + 侧壁」的合并结果缓存（原模型每次返回同一个 list 实例时复用）。 */
    private List<BakedQuad> merged;
    private List<BakedQuad> mergedFrom;

    private AutoSideWallModel(BakedModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
        List<BakedQuad> original = delegate.getQuads(state, face, random);
        // 物品模型约定：不按面剔除的那一批（face == null）才是「能看到的面」，侧壁挂在这里，
        // 原版 ItemRenderer.renderModelLists 与我们自己的 emitQuads 都会遍历到它
        if (state != null || face != null) {
            return original;
        }
        List<BakedQuad> extra = walls();
        if (extra.isEmpty()) {
            return original;
        }
        if (merged == null || mergedFrom != original) {
            List<BakedQuad> combined = new ArrayList<>(original.size() + extra.size());
            combined.addAll(original);
            combined.addAll(extra);
            merged = combined;
            mergedFrom = original;
        }
        return merged;
    }

    private List<BakedQuad> walls() {
        if (!wallsBuilt) {
            wallsBuilt = true;
            walls = buildWalls();
        }
        return walls;
    }

    // ==================== 侧壁生成 ====================

    private List<BakedQuad> buildWalls() {
        List<Sheet> sheets = collectSheets();
        if (sheets.isEmpty()) {
            return List.of();
        }
        // 模型自带的面（位置签名）——补侧壁时跳过它们，避免同一位置叠两层半透明几何
        Set<String> known = new HashSet<>();
        for (Sheet sheet : sheets) {
            known.add(signature(sheet.pos));
        }
        Map<String, List<Sheet>> groups = new LinkedHashMap<>();
        for (Sheet sheet : sheets) {
            groups.computeIfAbsent(sheet.groupKey(), key -> new ArrayList<>()).add(sheet);
        }
        List<BakedQuad> result = new ArrayList<>();
        for (List<Sheet> group : groups.values()) {
            buildGroupWalls(group, known, result);
        }
        return List.copyOf(result);
    }

    /** 收集模型里的全部面片（含按面剔除的那几批，去重）。 */
    private List<Sheet> collectSheets() {
        List<BakedQuad> quads = new ArrayList<>();
        Set<BakedQuad> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        RandomSource random = RandomSource.create(42L);
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            addQuads(delegate.getQuads(null, direction, random), quads, seen);
        }
        random.setSeed(42L);
        addQuads(delegate.getQuads(null, null, random), quads, seen);
        List<Sheet> sheets = new ArrayList<>();
        for (BakedQuad quad : quads) {
            Sheet sheet = Sheet.of(quad);
            if (sheet != null) {
                sheets.add(sheet);
            }
        }
        return sheets;
    }

    private static void addQuads(List<BakedQuad> from, List<BakedQuad> to, Set<BakedQuad> seen) {
        for (BakedQuad quad : from) {
            if (seen.add(quad)) {
                to.add(quad);
            }
        }
    }

    /** 一个矩形上的所有面片：薄的话补侧壁。 */
    private static void buildGroupWalls(List<Sheet> group, Set<String> known, List<BakedQuad> out) {
        float aMin = Float.MAX_VALUE;
        float aMax = -Float.MAX_VALUE;
        boolean positive = false;
        boolean negative = false;
        Sheet front = group.get(0);
        for (Sheet sheet : group) {
            aMin = Math.min(aMin, sheet.a);
            aMax = Math.max(aMax, sheet.a);
            if (sheet.sign > 0) {
                positive = true;
            } else {
                negative = true;
            }
            if (sheet.a < front.a) {
                front = sheet;
            }
        }
        // 只有「同一张矩形上存在正反两个朝向」的才算平面物品（两层面片 / 0 厚度的 element）。
        // 单面的组不碰：真·立体元素（6 面的薄盒子）的每一张侧面也是单面的平面，
        // 那样会围着盒子的每条边补出 1/16 宽的碎片；而盒子的正反面组补出来的四面侧壁
        // 与它自己的侧面位置完全重合，会被 known 签名挡掉。
        if (!positive || !negative) {
            return;
        }
        float thickness = aMax - aMin;
        if (thickness > THICKNESS + EPS) {
            // 本来就是有厚度的元素：它有可能是真立体，交给模型自己的几何
            return;
        }
        float pad = Math.max(0.0F, (THICKNESS - thickness) * 0.5F);
        float a0 = aMin - pad;
        float a1 = aMax + pad;
        float p0 = front.minP;
        float p1 = front.maxP;
        float q0 = front.minQ;
        float q1 = front.maxQ;
        if (p1 - p0 <= EPS && q1 - q0 <= EPS) {
            // 退化成一条线 / 一个点：无厚度可补
            return;
        }
        // 每个侧壁 4 个角点的局部坐标 (P, Q, A)：顺序与默认模型 drawQuad 一致（正面朝外、不被背面剔除）
        // ① P = p0，法线 -P
        addWall(front, known, out, direction(front.pAxis, false),
                new float[] { p0, q1, a0, p0, q0, a0, p0, q0, a1, p0, q1, a1 });
        // ② P = p1，法线 +P
        addWall(front, known, out, direction(front.pAxis, true),
                new float[] { p1, q0, a0, p1, q1, a0, p1, q1, a1, p1, q0, a1 });
        // ③ Q = q1，法线 +Q
        addWall(front, known, out, direction(front.qAxis, true),
                new float[] { p1, q1, a0, p0, q1, a0, p0, q1, a1, p1, q1, a1 });
        // ④ Q = q0，法线 -Q
        addWall(front, known, out, direction(front.qAxis, false),
                new float[] { p0, q0, a0, p1, q0, a0, p1, q0, a1, p0, q0, a1 });
    }

    /**
     * 补一个侧壁。
     *
     * @param source    贴图 / 顶点数据的来源面片
     * @param known     已有面的位置签名（重复的面直接跳过）
     * @param out       收集结果
     * @param direction 侧壁朝外的方向
     * @param layout    4 个角点的局部坐标，依次为 (P, Q, A) × 4
     */
    private static void addWall(Sheet source, Set<String> known, List<BakedQuad> out, Direction direction,
            float[] layout) {
        int axis = source.axis;
        int p = source.pAxis;
        int q = source.qAxis;
        float[][] pos = new float[4][];
        for (int i = 0; i < 4; i++) {
            float[] vertex = new float[3];
            vertex[p] = layout[i * 3];
            vertex[q] = layout[i * 3 + 1];
            vertex[axis] = layout[i * 3 + 2];
            pos[i] = vertex;
        }
        if (!known.add(signature(pos))) {
            return;
        }
        out.add(copyQuad(source, pos, direction));
    }

    /**
     * 用来源面片的顶点数据造一个新的面：位置换成给定坐标，UV / 颜色 / 贴图沿用来源面片
     * （UV 取该角在来源面片上的那一份，厚度方向自然退化）。
     */
    private static BakedQuad copyQuad(Sheet source, float[][] pos, Direction direction) {
        int stride = source.vertexStride();
        int[] data = new int[4 * stride];
        for (int i = 0; i < 4; i++) {
            int src = source.nearestVertex(pos[i]);
            System.arraycopy(source.data, src * stride, data, i * stride, stride);
            data[i * stride] = Float.floatToRawIntBits(pos[i][0]);
            data[i * stride + 1] = Float.floatToRawIntBits(pos[i][1]);
            data[i * stride + 2] = Float.floatToRawIntBits(pos[i][2]);
        }
        return new BakedQuad(data, source.quad.getTintIndex(), direction, source.quad.getSprite(),
                source.quad.isShade());
    }

    /** 某个轴向上、朝指定一侧的方向。 */
    private static Direction direction(int axis, boolean positive) {
        return switch (axis) {
            case 0 -> positive ? Direction.EAST : Direction.WEST;
            case 1 -> positive ? Direction.UP : Direction.DOWN;
            default -> positive ? Direction.SOUTH : Direction.NORTH;
        };
    }

    /** 4 个顶点位置（与顶点顺序无关）的量化签名，用来认「这个位置已经有面了」。 */
    private static String signature(float[][] pos) {
        int[][] keys = new int[4][3];
        for (int i = 0; i < 4; i++) {
            for (int axis = 0; axis < 3; axis++) {
                keys[i][axis] = quant(pos[i][axis]);
            }
        }
        Arrays.sort(keys, (left, right) -> {
            for (int axis = 0; axis < 3; axis++) {
                int compare = Integer.compare(left[axis], right[axis]);
                if (compare != 0) {
                    return compare;
                }
            }
            return 0;
        });
        StringBuilder builder = new StringBuilder(48);
        for (int[] key : keys) {
            builder.append(key[0]).append(',').append(key[1]).append(',').append(key[2]).append(';');
        }
        return builder.toString();
    }

    private static int quant(float value) {
        return Math.round(value * QUANT);
    }

    // ==================== 面片 ====================

    /** 模型里的一张「轴对齐矩形面片」。 */
    private static final class Sheet {

        final BakedQuad quad;
        final int[] data;
        /** 法线所在的轴（0=X / 1=Y / 2=Z）：该轴上 4 个顶点的跨度≈0。 */
        final int axis;
        /** 平面内的两条轴，与 {@link #axis} 构成右手系（P × Q = 法线轴）。 */
        final int pAxis;
        final int qAxis;
        /** 面片在 {@link #axis} 上的坐标。 */
        final float a;
        /** 法线在 {@link #axis} 上的符号（+1 / -1）。 */
        final int sign;
        final float minP;
        final float maxP;
        final float minQ;
        final float maxQ;
        final float[][] pos;

        private Sheet(BakedQuad quad, int[] data, int axis, int pAxis, int qAxis, float a, int sign,
                float minP, float maxP, float minQ, float maxQ, float[][] pos) {
            this.quad = quad;
            this.data = data;
            this.axis = axis;
            this.pAxis = pAxis;
            this.qAxis = qAxis;
            this.a = a;
            this.sign = sign;
            this.minP = minP;
            this.maxP = maxP;
            this.minQ = minQ;
            this.maxQ = maxQ;
            this.pos = pos;
        }

        /** 解析一个 quad；不是轴对齐的平面（旋转过的面片、异形面）返回 null。 */
        static Sheet of(BakedQuad quad) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            if (stride < 6) {
                return null;
            }
            float[][] pos = new float[4][3];
            for (int i = 0; i < 4; i++) {
                int base = i * stride;
                pos[i][0] = Float.intBitsToFloat(data[base]);
                pos[i][1] = Float.intBitsToFloat(data[base + 1]);
                pos[i][2] = Float.intBitsToFloat(data[base + 2]);
            }
            int axis = -1;
            float thinnest = Float.MAX_VALUE;
            float[] min = new float[3];
            float[] max = new float[3];
            for (int a = 0; a < 3; a++) {
                float low = Float.MAX_VALUE;
                float high = -Float.MAX_VALUE;
                for (int i = 0; i < 4; i++) {
                    low = Math.min(low, pos[i][a]);
                    high = Math.max(high, pos[i][a]);
                }
                min[a] = low;
                max[a] = high;
                float spread = high - low;
                if (spread < thinnest) {
                    thinnest = spread;
                    axis = a;
                }
            }
            if (axis < 0 || thinnest > EPS) {
                return null;
            }
            int sign = signOf(quad, pos, axis);
            if (sign == 0) {
                return null;
            }
            // P、Q 与法线轴构成右手系（P × Q = 法线轴），这样按默认模型的绕序规则生成的侧壁正面朝外
            int pAxis = (axis + 1) % 3;
            int qAxis = (axis + 2) % 3;
            return new Sheet(quad, data, axis, pAxis, qAxis, min[axis], sign,
                    min[pAxis], max[pAxis], min[qAxis], max[qAxis], pos);
        }

        /** 法线方向：优先按顶点绕序（渲染剔除用的就是它），退化时退回 quad 自带的方向。 */
        private static int signOf(BakedQuad quad, float[][] pos, int axis) {
            float ux = pos[1][0] - pos[0][0];
            float uy = pos[1][1] - pos[0][1];
            float uz = pos[1][2] - pos[0][2];
            float vx = pos[2][0] - pos[0][0];
            float vy = pos[2][1] - pos[0][1];
            float vz = pos[2][2] - pos[0][2];
            float cross = switch (axis) {
                case 0 -> uy * vz - uz * vy;
                case 1 -> uz * vx - ux * vz;
                default -> ux * vy - uy * vx;
            };
            if (Math.abs(cross) > EPS) {
                return cross > 0 ? 1 : -1;
            }
            Direction direction = quad.getDirection();
            if (direction != null) {
                int step = switch (axis) {
                    case 0 -> direction.getStepX();
                    case 1 -> direction.getStepY();
                    default -> direction.getStepZ();
                };
                if (step != 0) {
                    return step;
                }
            }
            return 0;
        }

        int vertexStride() {
            return data.length / 4;
        }

        /** 离给定位置最近的顶点下标（侧壁的角取来源面片对应角的 UV）。 */
        int nearestVertex(float[] target) {
            int best = 0;
            float bestDistance = Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                float distance = Math.abs(pos[i][pAxis] - target[pAxis]) + Math.abs(pos[i][qAxis] - target[qAxis]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            return best;
        }

        /** 分组键：同一个平面轴 + 同一个矩形范围 = 同一张薄片。 */
        String groupKey() {
            return axis + "|" + quant(minP) + "|" + quant(maxP) + "|" + quant(minQ) + "|" + quant(maxQ);
        }
    }

    // ==================== 其余接口：全部转交原模型 ====================

    @Override
    public boolean useAmbientOcclusion() {
        return delegate.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return delegate.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return delegate.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return delegate.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return delegate.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return delegate.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return delegate.getOverrides();
    }
}
