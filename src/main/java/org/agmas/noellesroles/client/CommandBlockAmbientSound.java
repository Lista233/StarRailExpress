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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.util.MyBackgroundAmbientLoop;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.NRSounds;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 命令方块事件的环境音效：仅当本局为命令方块事件（破坏进度条可见）时，以较小音量循环播放
 * {@link NRSounds#COMMAND_BLOCK}，作为环境音效（AMBIENT）全程伴随；对局结束后进度条隐藏，
 * 音效淡出并停止。
 *
 * <p>做法对齐 {@code OutsideSoundManager}：用 {@link MyBackgroundAmbientLoop} 的播放谓词控制启停，
 * 谓词归假即淡出，无需在对局结束处额外手动打断。</p>
 */
public final class CommandBlockAmbientSound {
    /** 环境音效音量（较小） */
    private static final float VOLUME = 0.25F;
    private static final int FADE_IN = 20;
    private static final int FADE_OUT = 20;
    private static final AtomicReference<SoundInstance> CURRENT = new AtomicReference<>();

    private CommandBlockAmbientSound() {
    }

    public static void register() {
        ClientTickEvents.START_WORLD_TICK.register(world -> {
            Minecraft client = Minecraft.getInstance();
            SoundManager soundManager = client.getSoundManager();
            if (soundManager == null) {
                return;
            }
            if (!shouldPlay(client)) {
                stop(soundManager);
                return;
            }
            SoundInstance sound = CURRENT.get();
            if (sound == null || !soundManager.isActive(sound)
                    || (sound instanceof MyBackgroundAmbientLoop loop && loop.isStopped())) {
                LocalPlayer player = client.player;
                if (player == null) {
                    return;
                }
                SoundInstance next = new MyBackgroundAmbientLoop(
                        player,
                        NRSounds.COMMAND_BLOCK,
                        SoundSource.AMBIENT,
                        VOLUME,
                        CommandBlockAmbientSound::alive,
                        FADE_IN, FADE_OUT);
                CURRENT.set(next);
                soundManager.play(next);
            }
        });
    }

    /** 本局是否为命令方块事件：进度条可见即代表事件进行中，对局结束后由服务端清零而归假。 */
    private static boolean shouldPlay(Minecraft client) {
        return client.player != null && CommandBlockProgressClient.isShowing();
    }

    private static boolean alive(LocalPlayer player) {
        return player != null && CommandBlockProgressClient.isShowing();
    }

    private static void stop(SoundManager soundManager) {
        SoundInstance old = CURRENT.getAndSet(null);
        if (old instanceof MyBackgroundAmbientLoop loop) {
            loop.tryStop();
        } else if (old != null && soundManager.isActive(old)) {
            soundManager.stop(old);
        }
    }
}
