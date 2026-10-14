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

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.util.ClientScheduler;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 让自定义列车物品「模型地址」指向的模型也能被烘焙。
 *
 * <p>
 * 资源包里<b>没有被任何物品 / 方块引用</b>的模型 json，默认不会被 {@code ModelBakery} 烘焙；
 * 这里在模型加载阶段把它们登记成 Fabric extra model（与 {@link GeneralModelLoadingPlugin}
 * 给皮肤模型做的事完全一样），之后就能用 {@code ModelResourceLocation} 取到。
 *
 * <p>
 * <b>模型烘焙只发生在资源加载阶段</b>，而「模型地址」是在游戏中途才拿到的（编辑界面保存 /
 * 服务端同步），所以这里除了在资源加载时登记，还提供 {@link #rebakeIfNeeded()}：
 * 一旦发现「需要的模型 id 里有还没烘焙过的」，就自动重载一次客户端资源（等价于 F3+T）把它烘焙上。
 * 少了这一步，{@code resolveConfiguredModel} 永远取不到模型，物品只能退回默认模型 ——
 * 外观就是一像素厚的方块贴了张贴图（前后两层 + 四面侧壁），而不是导入的立体模型。
 */
@Environment(EnvType.CLIENT)
public class CustomItemModelPlugin implements ModelLoadingPlugin {

    /** 上一次资源加载时登记过的模型 id（用来判断有没有新增、需不需要重新烘焙）。 */
    private static volatile Set<ResourceLocation> bakedIds = Set.of();

    /** 资源正忙时的重试次数（避免无限排队）。 */
    private static int retry;

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        Set<ResourceLocation> ids = collectModelIds();
        bakedIds = Set.copyOf(ids);
        retry = 0;
        if (ids.isEmpty()) {
            return;
        }
        pluginContext.addModels(ids.toArray(new ResourceLocation[0]));
        SRE.LOGGER.info("[CustomItem] Registered {} extra item model(s)", ids.size());
    }

    /**
     * 「模型地址」有新模型时自动重载一次客户端资源，把它们烘焙上。
     *
     * <p>
     * 编辑界面保存、以及收到服务端同步之后调用。只在<b>真的有没烘焙过的 id</b> 时才动作，
     * 所以同一批物品反复进出服务器不会反复重载；资源正在加载（例如正在应用服务器资源包）时
     * 延后一秒重试。
     */
    public static void rebakeIfNeeded() {
        Set<ResourceLocation> needed = collectModelIds();
        if (needed.isEmpty() || bakedIds.containsAll(needed)) {
            retry = 0;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            // 不在世界里（主菜单等）：下次进世界的资源加载自然会带上这些模型
            return;
        }
        if (minecraft.getOverlay() != null) {
            // 资源正在加载：等加载完再试
            if (retry++ < 20) {
                ClientScheduler.schedule(CustomItemModelPlugin::rebakeIfNeeded, 20);
            }
            return;
        }
        SRE.LOGGER.info("[CustomItem] {} model address model(s) not baked yet, reloading client resources",
                needed.size());
        minecraft.execute(minecraft::reloadResourcePacks);
    }

    /** 所有自定义物品里填了「模型地址」的模型 id（去重，保持配置顺序）。 */
    public static Set<ResourceLocation> collectModelIds() {
        // 客户端的内存索引只在「收到服务端同步」或「编辑界面保存」之后才加载；启动、资源重载时
        // 它可能还是空的，那样模型地址永远不会被登记、也就永远不会被烘焙。这里按需补读一次
        // config 目录里的副本（索引已经有内容时什么都不做）。
        CustomItemLoader.ensureClientIndexLoaded();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (CustomItemData data : CustomItemLoader.getAllData()) {
            if (data == null || data.modelPath == null || data.modelPath.isBlank()) {
                continue;
            }
            ResourceLocation id = CustomItemData.resolveModelId(data.modelPath);
            if (id != null) {
                ids.add(id);
            }
            for (String modelPath : data.animatedModelFramePaths()) {
                ResourceLocation frameId = CustomItemData.resolveModelId(modelPath);
                if (frameId != null) {
                    ids.add(frameId);
                }
            }
        }
        return ids;
    }
}
