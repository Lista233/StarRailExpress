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

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EmbalmerRoleData extends SimpleRoleData {


    public static final int MASQUERADE_COOLDOWN = 150 * 20;
    public static final int MASQUERADE_INITIAL_COOLDOWN = 150 * 20; // 开局2分半冷却
    public static final int MASQUERADE_DURATION = 30 * 20;
    public static final int PITCH_MIN = 70;
    public static final int PITCH_MAX = 130;

    public int masqueradeCooldown;
    public boolean masqueradeActive;
    public int masqueradeTicksLeft;
    /** 语音互换（背包 Screen）的独立冷却，避免与统一技能系统的冷却字段冲突。 */
    public int voiceSwapCooldown;
    // Sync: swap map + pitch map as UUID-string pairs
    public Map<UUID, UUID> skinSwaps = new HashMap<>();
    public Map<UUID, Float> voicePitches = new HashMap<>();

    // 用于检测激活状态边界，避免每秒同步
    private boolean prevMasqueradeActive = false;

    public EmbalmerRoleData(RoleDataContext context) {
        super(context);
    }


    @Override
    public void init() {
        masqueradeCooldown = MASQUERADE_INITIAL_COOLDOWN; // 开局进入2分半冷却
        masqueradeActive = false;
        masqueradeTicksLeft = 0;
        voiceSwapCooldown = 0;
        skinSwaps.clear();
        voicePitches.clear();
        prevMasqueradeActive = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }


    public boolean isActive() {
        if (player == null || player.level().isClientSide())
            return false;
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.EMBALMER);
    }

    @Override
    public void serverTick() {
        if (!isActive())
            return;
        boolean changed = false;
        if (masqueradeCooldown > 0) {
            masqueradeCooldown--;
            if (masqueradeCooldown == 0)
                changed = true; // 冷却归零边界
        }
        if (voiceSwapCooldown > 0) {
            voiceSwapCooldown--;
            if (voiceSwapCooldown == 0)
                changed = true;
        }
        if (masqueradeActive && masqueradeTicksLeft > 0) {
            masqueradeTicksLeft--;
            if (masqueradeTicksLeft <= 0) {
                masqueradeActive = false;
                skinSwaps.clear();
                voicePitches.clear();
                // 发送清除数据包到所有客户端，重置皮肤和音调
                if (player instanceof ServerPlayer sp) {
                    for (ServerPlayer p : sp.serverLevel().getPlayers(p2 -> true)) {
                        ServerPlayNetworking.send(p, org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket.clear());
                    }
                }
                changed = true; // 激活结束边界
            }
        }
        // 仅在状态边界同步（替代原先每秒同步）：冷却归零、激活开始/结束。
        // 客户端 HUD 自行平滑秒数显示，因此无需每秒发包。
        if (masqueradeActive != prevMasqueradeActive)
            changed = true;
        if (changed)
            sync();
        prevMasqueradeActive = masqueradeActive;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("masqueradeCooldown", masqueradeCooldown);
        tag.putBoolean("masqueradeActive", masqueradeActive);
        tag.putInt("masqueradeTicksLeft", masqueradeTicksLeft);
        tag.putInt("voiceSwapCooldown", voiceSwapCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        masqueradeCooldown = tag.getInt("masqueradeCooldown");
        masqueradeActive = tag.getBoolean("masqueradeActive");
        masqueradeTicksLeft = tag.getInt("masqueradeTicksLeft");
        voiceSwapCooldown = tag.getInt("voiceSwapCooldown");
    }

    /**
     * Get voice pitch for a player during active masquerade. Returns 1.0F if not
     * active or not found.
     */
    public static float getVoicePitch(Player player) {
        if (player == null || player.level().isClientSide())
            return 1.0F;
        var comp = RoleData.getNullable(EmbalmerRoleData.class, player);
        if (!RoleData.isAttached(comp) || !comp.masqueradeActive)
            return 1.0F;
        return comp.voicePitches.getOrDefault(player.getUUID(), 1.0F);
    }



    @Override
    public void clientTick() {
        if (masqueradeCooldown > 0) {
            masqueradeCooldown--;
        }
        if (voiceSwapCooldown > 0) {
            voiceSwapCooldown--;
        }
        if (masqueradeActive && masqueradeTicksLeft > 0) {
            masqueradeTicksLeft--;
        }
    }

    /**
     * 技能一「变身派对」：全场玩家互相交换皮肤并随机改变音调。
     *
     * <p>原实现位于 {@code ModPacketsReciever} 的 {@code EmbalmerC2SPacket} 接收器，
     * 为了同时支持「统一技能系统（G 键 / V 键切换）」抽出为静态方法复用。
     *
     * @return 是否成功发动（冷却中或人数不足时返回 false）
     */
    public static boolean triggerMasquerade(ServerPlayer player) {
        var comp = RoleData.getNullable(EmbalmerRoleData.class, player);
        if (comp == null) {
            return false;
        }
        if (comp.masqueradeCooldown > 0) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.embalmer.cooldown",
                            (comp.masqueradeCooldown + 19) / 20).withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        java.util.List<ServerPlayer> players = player.serverLevel().getPlayers(
                p2 -> !p2.isSpectator()
                        && !org.agmas.harpymodloader.component.WorldModifierComponent.KEY.get(p2.level())
                                .isModifier(p2, pro.fazeclan.river.stupid_express.constants.SEModifiers.JEB_));
        if (players.size() < 2) {
            player.displayClientMessage(Component.literal("Need at least 2 players."), true);
            return false;
        }

        java.util.Map<UUID, UUID> swaps = new java.util.LinkedHashMap<>();
        java.util.Map<UUID, Float> pitches = new java.util.HashMap<>();
        java.util.List<UUID> uuids = new java.util.ArrayList<>();
        for (ServerPlayer p : players) {
            uuids.add(p.getUUID());
        }
        java.util.Collections.shuffle(uuids, new java.util.Random());
        for (int i = 0; i < uuids.size(); i++) {
            UUID from = players.get(i).getUUID();
            UUID to = uuids.get(i);
            if (from.equals(to)) {
                to = uuids.get((i + 1) % uuids.size());
            }
            swaps.put(from, to);
            pitches.put(from, 0.7F + (new java.util.Random().nextFloat() * 0.6F));
        }
        comp.skinSwaps = swaps;
        comp.voicePitches = pitches;
        comp.masqueradeActive = true;
        comp.masqueradeTicksLeft = MASQUERADE_DURATION;
        comp.masqueradeCooldown = MASQUERADE_COOLDOWN;
        comp.sync();

        // 全场播放音效（遍历所有玩家，绕过距离衰减）
        for (ServerPlayer p : player.serverLevel().getPlayers(p2 -> true)) {
            p.playNotifySound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.MASTER, 1.0F, 1.0F);
        }
        // 广播皮肤交换数据给所有玩家
        org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket swapPacket =
                new org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket(swaps, pitches, MASQUERADE_DURATION);
        for (ServerPlayer p : player.serverLevel().getPlayers(p2 -> true)) {
            ServerPlayNetworking.send(p, swapPacket);
        }
        player.displayClientMessage(
                Component.translatable("message.noellesroles.embalmer.activated").withStyle(ChatFormatting.DARK_PURPLE),
                true);
        return true;
    }
}
