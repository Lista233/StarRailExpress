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

package io.wifi.starrailexpress.content.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 职业阵营导出指令：{@code /sre:export_roles}。
 * <p>
 * 遍历运行时职业注册表 {@link TMMRoles#ROLES}（含自定义职业），用
 * {@link PlayerRoleWeightManager#getRoleType(SRERole)} 把每个职业归入
 * 义警 / 平民 / 杀手 / 中立（杀手中立并入中立）四类，
 * 以完整 id（{@code namespace:path}，与数据库 roleStats 键一致）为键写成平铺 JSON，
 * 输出到服务端运行目录（gameDir）下的 {@code role_factions.json}。
 * <p>
 * 运行时导出天然覆盖内置与自定义职业，无需静态解析源码。
 */
public final class RoleFactionExportCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 导出文件名（相对于服务端运行目录）。 */
    private static final String OUTPUT_FILE = "role_factions.json";

    /** 展示用的阵营顺序与标签。 */
    private static final List<String> FACTION_LABELS = List.of("义警", "平民", "杀手", "中立");

    /** getRoleType 无法判定时使用的标签（正常不应出现，仅防御）。 */
    private static final String UNKNOWN_LABEL = "未分类";

    private RoleFactionExportCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:export_roles")
                .requires(source -> source.hasPermission(2))
                .executes(context -> exportRoleFactions(context.getSource())));
    }

    private static int exportRoleFactions(CommandSourceStack source) {
        // 键 = 完整职业 id（namespace:path），值 = 阵营标签；TreeMap 保证输出按 id 排序、可复现。
        Map<String, String> factions = new TreeMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String label : FACTION_LABELS) {
            counts.put(label, 0);
        }
        List<String> unknownRoles = new ArrayList<>();

        for (SRERole role : TMMRoles.ROLES.values()) {
            String id = role.identifier().toString();
            String label = factionLabel(PlayerRoleWeightManager.getRoleType(role));
            if (label == null) {
                unknownRoles.add(id);
                factions.put(id, UNKNOWN_LABEL);
            } else {
                factions.put(id, label);
                counts.merge(label, 1, Integer::sum);
            }
        }

        try {
            Path file = FabricLoader.getInstance().getGameDir().resolve(OUTPUT_FILE);
            Files.writeString(file, GSON.toJson(factions) + "\n", StandardCharsets.UTF_8);

            source.sendSuccess(() -> Component.literal("职业阵营映射已导出到: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(file.toAbsolutePath().toString()).withStyle(ChatFormatting.WHITE)), false);
            source.sendSuccess(() -> Component.literal("共 " + factions.size() + " 个职业（"
                    + FACTION_LABELS.stream()
                            .map(label -> label + ": " + counts.getOrDefault(label, 0))
                            .collect(Collectors.joining("，"))
                    + "）"), false);
            if (!unknownRoles.isEmpty()) {
                source.sendSuccess(() -> Component.literal("无法判定阵营的职业（已标为" + UNKNOWN_LABEL + "）: "
                        + String.join(", ", unknownRoles)).withStyle(ChatFormatting.YELLOW), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("导出职业阵营失败: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * 把 {@link PlayerRoleWeightManager#getRoleType(SRERole)} 的类型编号转换为阵营标签。
     *
     * @return 义警 / 平民 / 杀手 / 中立；无法判定（-1）返回 {@code null}
     */
    private static String factionLabel(int roleType) {
        return switch (roleType) {
            case 1 -> "平民";
            case 2, 3 -> "中立"; // 3 = 杀手中立，并入中立
            case 4 -> "杀手";
            case 5 -> "义警";
            default -> null;
        };
    }
}
