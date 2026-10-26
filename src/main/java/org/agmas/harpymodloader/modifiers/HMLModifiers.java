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

package org.agmas.harpymodloader.modifiers;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HMLModifiers {

    public static final ArrayList<SREModifier> MODIFIERS = new ArrayList<>();

    /**
     * 修饰符注册表锁。
     *
     * <p>
     * 自定义内容的注册 / 重载会同时发生在两条线程上：<b>服务端线程</b>（命令、级联重载）与
     * <b>客户端线程</b>（同步内容下发后的应用，见 {@code ContentSyncClient}）。单机下两者操作的是
     * 同一份静态注册表，交错执行会让「本来就唯一的 id」被判成重复、索引与列表也会不同步，因此
     * 所有改动 {@link #MODIFIERS} 的流程都要持有本锁。
     *
     * <p>
     * 注意粒度：一次重载（清空 + 重新注册）必须<b>整体</b>持锁，只锁单次注册挡不住交错。
     *
     * @see io.wifi.starrailexpress.custommodifier.CustomModifierLoader
     */
    public static final Object LOCK = new Object();

    /**
     * 完整 identifier -> 修饰符。重复注册校验的<b>唯一</b>依据。
     *
     * <p>
     * 键是完整 id（含命名空间）：命名空间不同的同名修饰符互不冲突，因此自定义修饰符
     * {@code custommodifier:xxx} 不会因为某个内置修饰符（例如 {@code 其他mod:xxx}）占用了同一 path
     * 而被误判成重复。
     */
    public static final Map<ResourceLocation, SREModifier> MODIFIERS_BY_ID = new HashMap<>();

    /**
     * path -> 修饰符，仅供按「路径简写」查找（见 {@link #getModifierByPath(String)}）。
     *
     * <p>
     * <b>不参与判重</b>：同一 path 允许存在多个命名空间不同的修饰符，本索引保留<b>最先注册</b>的那个
     * （内置修饰符在模组初始化时注册，早于配置驱动的自定义修饰符），因此简写查询的结果是稳定的，
     * 不受重载顺序影响。
     */
    public static final Map<String, SREModifier> MODIFIERS_BY_PATH = new HashMap<>();

    private static final HashSet<String> CACHED_VERSIONS_LIST = new HashSet<>();

    public static SREModifier getModifier(ResourceLocation res) {
        if (res == null)
            return null;
        return MODIFIERS_BY_ID.get(res);
    }

    /**
     * 按 identifier 的 path 取修饰符（忽略命名空间）。
     *
     * <p>
     * 只作「路径简写」的兜底解析，多个命名空间共用同一 path 时返回最先注册的那个；完整 id 请用
     * {@link #getModifier(ResourceLocation)}。
     *
     * @param path 修饰符 id 的 path 部分
     * @return 对应修饰符，不存在时返回 {@code null}
     */
    public static SREModifier getModifierByPath(String path) {
        if (path == null)
            return null;
        return MODIFIERS_BY_PATH.get(path);
    }

    public static void init() {
        // 修饰符互斥（SREModifier#addTwoWayOpposingModifier）的运行时兜底
        ModifierOpposingHelper.init();
    }

    public static SREModifier register(SREModifier modifier) {
        return registerModifier(modifier);
    }

    public static SREModifier register(SREModifier modifier, String... flags) {
        return registerModifier(modifier, flags);
    }

    public static SREModifier registerModifier(SREModifier modifier, String... flags) {
        return registerModifier(modifier.addFlag(flags));
    }

    /**
     * 注册修饰符。
     *
     * <p>
     * 与 {@code TMMRoles.registerRole} 对应：同一个<b>完整 identifier</b>（命名空间 + path）重复注册
     * 直接抛异常，避免重复实例污染 {@link #MODIFIERS}。命名空间不同的同名修饰符互不影响——这正是
     * 自定义内容能靠 {@code custommodifier} 命名空间与内置修饰符隔离的前提。配置驱动的自定义修饰符
     * 请改用 {@link #registerCustomModifier(SREModifier)}。
     *
     * @throws IllegalArgumentException 该 identifier 已被注册
     */
    public static SREModifier registerModifier(SREModifier modifier) {
        synchronized (LOCK) {
            ResourceLocation id = modifier.identifier();
            if (id != null && MODIFIERS_BY_ID.containsKey(id)) {
                // 拒绝注册
                throw new IllegalArgumentException(String.format(
                        "[MODIFIER REGISTERER] Duplicated modifier identifier found: %s. Ignore the new one.",
                        id));
            }
            MODIFIERS.add(modifier);
            if (id != null) {
                MODIFIERS_BY_ID.put(id, modifier);
                // 简写索引保留最先注册者：内置修饰符先于自定义修饰符注册，简写一律解析到内置
                MODIFIERS_BY_PATH.putIfAbsent(id.getPath(), modifier);
            }
            return modifier;
        }
    }

    /**
     * 注册配置驱动的自定义修饰符。
     *
     * <p>
     * 与 {@code TMMRoles.registerCustomRole} 一致：重复注册不抛异常，记录错误并返回 {@code null}，
     * 调用方据此跳过该条配置。判定键与 {@link #registerModifier(SREModifier)} 相同——<b>完整
     * identifier</b>，因此自定义修饰符只会与「同一命名空间下的同名 id」冲突，不会因为与某个内置
     * 修饰符共用 path（例如都叫 {@code tiny}）而被拒。
     *
     * @return 注册成功返回修饰符本身；该 identifier 已被占用时返回 {@code null}
     */
    public static SREModifier registerCustomModifier(SREModifier modifier) {
        synchronized (LOCK) {
            ResourceLocation id = modifier.identifier();
            if (id != null && MODIFIERS_BY_ID.containsKey(id)) {
                SRE.LOGGER.error(
                        "[MODIFIER REGISTERER] Duplicated modifier identifier found: {}. Ignore the new one.",
                        id);
                return null;
            }
            return registerModifier(modifier);
        }
    }

    /**
     * 注销修饰符，同时清理 {@link #MODIFIERS_BY_ID} 与 {@link #MODIFIERS_BY_PATH} 索引。
     *
     * <p>
     * 两个索引都只在当前确实指向该实例时才移除，避免误删此后注册的同一 id / 同一 path 修饰符。
     *
     * @return 是否执行了注销（{@code modifier} 为 {@code null} 时返回 {@code false}）
     */
    public static boolean unregisterModifier(SREModifier modifier) {
        if (modifier == null)
            return false;
        synchronized (LOCK) {
            MODIFIERS.remove(modifier);
            ResourceLocation id = modifier.identifier();
            if (id != null) {
                if (MODIFIERS_BY_ID.get(id) == modifier) {
                    MODIFIERS_BY_ID.remove(id);
                }
                if (MODIFIERS_BY_PATH.get(id.getPath()) == modifier) {
                    MODIFIERS_BY_PATH.remove(id.getPath());
                }
            }
            return true;
        }
    }

    /** 与 {@code TMMRoles.unregisterCustomRole} 对应的别名。 */
    public static boolean unregisterCustomModifier(SREModifier modifier) {
        return unregisterModifier(modifier);
    }

    /**
     * 取所有「绑定了该职业」的修饰符（{@link SREModifier#addBoundRole} 声明）。
     *
     * <p>
     * 分配器在随机刷新结束后用它做强制补发：这些职业的玩家必然拿到这些修饰符，
     * 不受刷新数量、生成概率与玩家修饰符上限限制。
     *
     * @param role 职业，{@code null} 时返回空集合
     */
    public static Set<SREModifier> getModifiersBoundToRole(SRERole role) {
        Set<SREModifier> result = new HashSet<>();
        if (role == null)
            return result;
        result.addAll(role.getBoundModifiers());
        return result;
    }

    public static HashSet<String> getAllFlags() {
        HashSet<String> filters = new HashSet<>();
        for (var it : MODIFIERS) {
            filters.addAll(it.getFlags());
        }
        return filters;
    }

    public static void refreshVersionTags() {
        CACHED_VERSIONS_LIST.clear();
        getAllAddedVersions();
    }

    public static Set<String> getAllAddedVersions() {
        if (!CACHED_VERSIONS_LIST.isEmpty()) {
            return new HashSet<>(CACHED_VERSIONS_LIST);
        }
        CACHED_VERSIONS_LIST.clear();
        for (var t : MODIFIERS) {
            CACHED_VERSIONS_LIST.add(t.getAddedVersion());
        }
        return new HashSet<>(CACHED_VERSIONS_LIST);
    }
}
