package org.agmas.noellesroles.game.command_block;

import java.util.List;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.killer.Error404PuppetRoleData;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 同化后的杀手阵营傀儡；没有原职业能力、商店或同化能力。 */
public final class Error404PuppetRole extends NormalRole {
    public Error404PuppetRole(ResourceLocation id) {
        super(id, 0x551080, false, true, MoodType.FAKE, Integer.MAX_VALUE, true);
        setRoleData(Error404PuppetRoleData::new);
        setCanBeRandomedByOtherRoles(false);
        setCanSetSpawnInfoInConfig(false);
        setDefaultMax(0);
        setHiddenForRoleRotation(true);
        setPassiveIncome(false);
        setCanUseInstinctAndNightVision(false);
        setCanSeeCoin(false);
    }
    @Override public List<ShopEntry> getShopEntries() { return List.of(); }
    @Override public List<ItemStack> getDefaultItems() { return List.of(ModItems.ERROR_CORE.getDefaultInstance()); }
}
