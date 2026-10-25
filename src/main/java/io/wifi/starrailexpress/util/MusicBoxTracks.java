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

package io.wifi.starrailexpress.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * 音乐盒曲目登记表。
 * <p>
 * 皮肤类型 {@link ItemSkinManager.SkinTypes#MUSIC_BOX} 的每个皮肤对应一首背景音乐：
 * 皮肤由内容模组（如 sre-skin）通过 {@link ItemSkinManager#registerACustomSkin} 注册，
 * 曲目在同一个初始化流程里调用 {@link #register(String, ResourceLocation)} 登记。
 * 音频文件（{@code sounds/*.ogg}）与 {@code sounds.json} 里的音效条目由内容模组提供，
 * 本类只记住「皮肤 ID → 音效 ID」的对应关系。
 *
 * <p>播放逻辑见 {@code io.wifi.starrailexpress.client.MusicBoxAmbientSound}：
 * 只有玩家实际装配了带曲目的音乐盒，且当局没有地图背景音、也不是命令方块事件局时才会循环播放。</p>
 */
public final class MusicBoxTracks {
    /** 皮肤 ID → 音效 ID（保持登记顺序，方便展示） */
    private static final Map<String, ResourceLocation> TRACKS = new LinkedHashMap<>();

    private MusicBoxTracks() {
    }

    /**
     * 登记一首音乐盒曲目。
     *
     * @param skinId  音乐盒皮肤 ID（不区分大小写）
     * @param soundId 音效 ID，必须与内容模组 {@code sounds.json} 中的键一致
     */
    public static void register(String skinId, ResourceLocation soundId) {
        if (skinId == null || skinId.isBlank() || soundId == null) {
            return;
        }
        TRACKS.put(normalize(skinId), soundId);
    }

    /** 取某个音乐盒皮肤对应的音效 ID；没有登记过返回 {@code null}。 */
    public static ResourceLocation getSoundId(String skinId) {
        if (skinId == null) {
            return null;
        }
        return TRACKS.get(normalize(skinId));
    }

    /** 该音乐盒皮肤是否登记过曲目。 */
    public static boolean hasTrack(String skinId) {
        return getSoundId(skinId) != null;
    }

    /** 所有已登记曲目的音乐盒皮肤 ID（只读）。 */
    public static Set<String> skinIds() {
        return Collections.unmodifiableSet(TRACKS.keySet());
    }

    private static String normalize(String skinId) {
        return skinId.toLowerCase(Locale.ROOT);
    }
}
