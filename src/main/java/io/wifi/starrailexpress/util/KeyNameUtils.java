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

package io.wifi.starrailexpress.util;

import java.util.List;

/**
 * 门 keyName 可能叠加 locked:/alarmed:/reinforced: 前缀；判断房间与钥匙对应关系时需要剥掉。
 */
public final class KeyNameUtils {
    private static final List<String> PREFIXES = List.of("locked:", "alarmed:", "reinforced:");

    private KeyNameUtils() {
    }

    public static String baseKeyName(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            return "";
        }
        String result = keyName;
        boolean changed;
        do {
            changed = false;
            for (String prefix : PREFIXES) {
                if (result.startsWith(prefix)) {
                    result = result.substring(prefix.length());
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    public static boolean isBound(String keyName) {
        return !baseKeyName(keyName).isEmpty();
    }
}
