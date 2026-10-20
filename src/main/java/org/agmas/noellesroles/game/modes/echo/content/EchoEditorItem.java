package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 回响地图编辑工具。
 *
 * <p>右键在客户端打开编辑界面（{@code EchoEditorScreen}），所有真正的地图读写都在服务端会话里完成：
 * 仅 OP2、仅非对局、每人一份草稿。手持时 HUD 常驻显示会话状态、草稿 revision 与地图模式声明。
 *
 * <p>物品本体不引用任何客户端类：界面由静态回调注入，与区域地图物品同一做法。
 */
public final class EchoEditorItem extends Item {
    /** 客户端设置：右键时打开编辑界面。 */
    public static Runnable openScreenCallback;

    public EchoEditorItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && openScreenCallback != null) openScreenCallback.run();
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.echo_editor.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.echo_editor.desc2").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }
}
