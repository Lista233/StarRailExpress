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

package pro.fazeclan.river.stupid_express.mixin.modifier.twin_children;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenHandler;

import java.util.Set;

/**
 * Twin Children must never be dragged across the world by one teleport.
 *
 * <p>Domains (warlock arena, travelogue banishment, tarot assembly) teleport one
 * twin tens of thousands of blocks away. The stacking logic would then follow
 * the partner through {@code positionRider} / re-mounting every tick and drop
 * it in the void when the domain collapses. This hook runs before the move and
 * releases the binding whenever the destination is farther than
 * {@link TwinChildrenHandler#MAX_BIND_TELEPORT_DISTANCE} from the partner.
 *
 * <p>Both entry points are guarded: the 7-arg variant (skill / domain / command
 * teleports, and the 3-arg variant which routes through it) and the 5-arg
 * dimension-transition variant. {@link TwinChildrenHandler#onTeleportStarting}
 * is idempotent, so the dimension path firing after the 7-arg path is a no-op.
 */
@Mixin(ServerPlayer.class)
public abstract class TwinChildrenTeleportMixin {

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"))
    private void stupidExpress$releaseTwinsOnFarTeleport(ServerLevel level, double x, double y, double z,
            Set<RelativeMovement> relativeMovements, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        TwinChildrenHandler.onTeleportStarting((ServerPlayer) (Object) this, x, y, z);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"))
    private void stupidExpress$releaseTwinsOnFarDimensionTeleport(ServerLevel level, double x, double y, double z,
            float yRot, float xRot, CallbackInfo ci) {
        TwinChildrenHandler.onTeleportStarting((ServerPlayer) (Object) this, x, y, z);
    }
}
