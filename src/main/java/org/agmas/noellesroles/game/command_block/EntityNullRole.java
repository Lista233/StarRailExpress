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
 * Null：未登记的乘客。
 *
 * <p>
 * 列车的照明、监控、广播、乘客名单里都没有他。他能把别人从名单上划掉，也能把整段记录涂黑。
 * <ul>
 * <li>被动「不在名单上」：捡不起地上的东西；列车照明下会变慢；断电时反而更快。</li>
 * <li>技能「抹除」：2 秒前摇（断电中只要 1 秒）视线锁定，一局最多成功 2 次，代价是删掉自己一件东西。</li>
 * <li>技能「涂黑」（切换技能后释放）：熄灭周围一圈灯并让监控失效，一局 1 次。</li>
 * </ul>
 */
public class EntityNullRole extends NormalRole {
    /** 抹除冷却（秒）。 */
    public static final int ERASE_COOLDOWN_SECONDS = 45;
    /** 涂黑冷却（秒）。 */
    public static final int REDACT_COOLDOWN_SECONDS = 90;
    /** 涂黑每局次数。 */
    public static final int REDACT_CHARGES = 1;

    public EntityNullRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.ENTITY_NULL,
                RoleSkill.skill(SRE.id("entity_null_erase"),
                        "skill.noellesroles.entity_null.erase",
                        EntityNullRole::erase)
                        .cooldownSeconds(ERASE_COOLDOWN_SECONDS).announceToSelf().showOnHud(true).build(),
                RoleSkill.skill(SRE.id("entity_null_redact"),
                        "skill.noellesroles.entity_null.redact",
                        EntityNullRole::redact)
                        .cooldownSeconds(REDACT_COOLDOWN_SECONDS).charges(REDACT_CHARGES)
                        .announceToSelf().showOnHud(true).build());
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
        if (data.isErasing()) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.busy")
                    .withStyle(ChatFormatting.GRAY), true);
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

    private static boolean redact(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        EntityNullRoleData data = RoleData.getNullable(EntityNullRoleData.class, self);
        if (data == null) {
            return false;
        }
        return data.redact(self);
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
