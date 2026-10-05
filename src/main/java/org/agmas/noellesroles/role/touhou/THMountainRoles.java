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

package org.agmas.noellesroles.role.touhou;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.NormalRole.RoleType;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.touhou.roles.THIbarakiKasenRole;
import org.agmas.noellesroles.role.touhou.roles.THKyoukoRole;
import org.agmas.noellesroles.role.touhou.roles.THRinnosukeRole;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class THMountainRoles {
    public static final String NAMESPACE = "th_mount";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static final ResourceLocation NITORI_ID = id("kawashiro_nitori");
    // 河城荷取。可以购买除了杀手道具外的各种东西，且可丢弃！但价格翻倍。
    public static SRERole NITORI = TMMRoles.registerRole(new THRinnosukeRole(
            NITORI_ID, // 角色 ID
            new Color(162, 221, 233).getRGB(),
            false, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true) {
        private static final List<ShopEntry> NITORI_SHOP = List.of(
                new ShopEntry(ModItems.DEALER_PACKAGE.getDefaultInstance(), 100, ShopEntry.Type.TOOL),
                new ShopEntry(TMMItems.DEFENSE_VIAL.getDefaultInstance(), 400, ShopEntry.Type.TOOL));

        @Override
        public List<ShopEntry> getShopEntries() {
            return NITORI_SHOP;
        }
    }, "th_mountain").setNeutrals(true).setDefaultEnableNeededPlayerCount(12).setDefaultEnableChance(100)
            .setCanUseInstinctAndNightVision(false).setCanPickUpRevolver(false)
            // 特殊中立
            .setSpecialNeutral(true);

    // 茨木华扇 Ibaraki Kasen
    public static SRERole IBARAKI_KASEN = TMMRoles.registerRole(new THIbarakiKasenRole(id("ibaraki_kasen"),
            new Color(216, 158, 159).getRGB(), true, false, MoodType.REAL,
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false), "th_mountain")
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(5000)
            .setAddedVersion("4.4");

    // 幽谷响子 kasodani_kyouko
    public static SRERole KASODANI_KYOUKO = TMMRoles.registerRole(new THKyoukoRole(id("kasodani_kyouko"),
            new Color(216, 158, 159).getRGB(), RoleType.CIVILIAN, MoodType.REAL,
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false), "th_mountain")
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(5000)
            .setAddedVersion("4.4");

    static {
        NITORI.setAddedVersion("4.3");
    }

    public static void init() {

    }
}
