package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.EntityNullRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Null：缺席的人。
 * 技能抹除需要 2 秒前摇，一局最多成功 2 次，成功后删掉自己一件东西或反噬。
 * 不能捡地上的物品，冲刺只有 1 秒，站在很亮的地方会变慢。
 */
public class EntityNullRole extends NormalRole {
    public EntityNullRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.ENTITY_NULL, RoleSkill.skill(
                SRE.id("entity_null_erase"),
                "skill.noellesroles.entity_null.erase",
                EntityNullRole::erase)
                .cooldownSeconds(45).announceToSelf().showOnHud(true).build());
    }

    private static boolean erase(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        EntityNullRoleData data = RoleData.getNullable(EntityNullRoleData.class, self);
        if (data == null) {
            return false;
        }
        if (data.erasesLeft() <= 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.empty")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return false;
        }
        ServerPlayer target = EntityNullRoleData.lookTarget(self);
        if (target == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.no_target")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        return data.beginErase(self, target, self.serverLevel().getGameTime());
    }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return TrueFalseResult.FALSE;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.VOID_STEP.getDefaultInstance(), 70, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}
