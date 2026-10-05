package org.agmas.noellesroles.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.net.URI;
import java.util.function.Supplier;

/**
 * PauseScreen 的 Mixin Accessor，用于访问私有字段和方法
 * 替代 Access Widener 以避免服务端加载客户端类
 */
@Environment(EnvType.CLIENT)
@Mixin(PauseScreen.class)
public interface PauseScreenAccessor {

    @Accessor("RETURN_TO_GAME")
    static Component getReturnToGame() {
        throw new AssertionError();
    }

    @Accessor("FEEDBACK_SUBSCREEN")
    static Component getFeedbackSubscreen() {
        throw new AssertionError();
    }

    @Accessor("SERVER_LINKS")
    static Component getServerLinks() {
        throw new AssertionError();
    }

    @Accessor("OPTIONS")
    static Component getOptions() {
        throw new AssertionError();
    }

    @Accessor("SHARE_TO_LAN")
    static Component getShareToLan() {
        throw new AssertionError();
    }

    @Accessor("PLAYER_REPORTING")
    static Component getPlayerReporting() {
        throw new AssertionError();
    }

    @Accessor("RETURN_TO_MENU")
    static Component getReturnToMenu() {
        throw new AssertionError();
    }

    @Accessor("SEND_FEEDBACK")
    static Component getSendFeedback() {
        throw new AssertionError();
    }

    @Accessor("REPORT_BUGS")
    static Component getReportBugs() {
        throw new AssertionError();
    }

    @Accessor("showPauseMenu")
    boolean isShowPauseMenu();

    @Accessor("disconnectButton")
    Button getDisconnectButton();

    @Accessor("disconnectButton")
    void setDisconnectButton(Button button);

    @Invoker("onDisconnect")
    void invokeOnDisconnect();

    @Invoker("openScreenButton")
    Button invokeOpenScreenButton(Component component, Supplier<Screen> screenSupplier);

    @Invoker("openLinkButton")
    Button invokeOpenLinkButton(Screen screen, Component component, URI uri);
}
