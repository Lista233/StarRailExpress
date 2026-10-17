package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.PaperDollRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * 纸人：技能立一个一次性替身，挡住一次非强制伤害后往后闪。
 * 成功后只虚弱 3 秒。冲刺不缩短，也不禁捡东西。
 */
public class PaperDollRole extends NormalRole {
    public static final ResourceLocation DEATH_CUT = org.agmas.noellesroles.Noellesroles.id("paper_cut");

    public PaperDollRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.PAPER_DOLL, RoleSkill.skill(
                SRE.id("paper_doll_arm"),
                "skill.noellesroles.paper_doll.arm",
                PaperDollRole::arm)
                .cooldownSeconds(32).announceToSelf().showOnHud(true).build());
    }

    public static boolean tryDodge(ServerPlayer self, ServerPlayer attacker, boolean tellAttacker) {
        if (self == null || !SREGameWorldComponent.KEY.get(self.level()).isRole(self, ModRoles.PAPER_DOLL)) {
            return false;
        }
        PaperDollRoleData data = RoleData.getNullable(PaperDollRoleData.class, self);
        if (data == null) {
            return false;
        }
        return data.absorb(self, attacker, tellAttacker) != 0;
    }

    private static boolean arm(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        PaperDollRoleData data = RoleData.getNullable(PaperDollRoleData.class, self);
        if (data == null) {
            return false;
        }
        long now = self.serverLevel().getGameTime();
        if (!data.arm(now)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.already")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        LegendFx.ring(self.serverLevel(), self.position().add(0.0D, 0.2D, 0.0D), 1.4F, LegendFx.PAPER);
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.35F, 1.7F);
        self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.armed")
                .withStyle(ChatFormatting.WHITE), true);
        return true;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.PAPER_CUT.getDefaultInstance(), 80, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}