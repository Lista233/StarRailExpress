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

package org.agmas.noellesroles;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.agmas.noellesroles.content.item.ora.OraRushState;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public interface ModDataComponentTypes {
   DataComponentType<CompoundTag> COOKED = register("cooked", (tagBuilder) -> {
      return tagBuilder.persistent(CompoundTag.CODEC);
   });

   /**
    * 欧拉一拳的连打状态镜像，详见 {@link org.agmas.noellesroles.content.item.ora.OraRushState}。
    * <p>
    * 只在连打期间存在，结束时会被移除，所以平时的欧拉一拳仍然是「没有任何组件」的干净物品。
    */
   DataComponentType<OraRushState> ORA_RUSH = register("ora_rush",
         (builder) -> builder.persistent(OraRushState.CODEC));

   private static <T> DataComponentType<T> register(String name,
         @NotNull UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
      return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Noellesroles.id(name),
            builderOperator.apply(DataComponentType.builder()).build());
   }

   public static Map<Integer, Float> getCookedFoodInfo(CompoundTag tag) {
      Map<Integer, Float> map = new HashMap<>();
      if (tag.contains("effects", Tag.TAG_LIST)) {
         ListTag listTag = tag.getList("effects", Tag.TAG_COMPOUND);
         for (Tag i : listTag) {
            if (i.getId() == CompoundTag.TAG_COMPOUND) {
               CompoundTag _t = (CompoundTag) i;
               if (_t.contains("time") && _t.contains("id")) {
                  map.put(_t.getInt("id"), _t.getFloat("time"));
               }
            }
         }
      }

      return map;
   }

   public static CompoundTag cookedFood(Map<Integer, Float> map) {
      var tag = new CompoundTag();
      var listTag = new ListTag();
      for (var it : map.entrySet()) {
         var _tag = new CompoundTag();
         _tag.putInt("id", it.getKey());
         _tag.putFloat("time", it.getValue());
         listTag.add(_tag);
      }
      tag.put("effects", listTag);
      return tag;
   }
}
