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

package io.wifi.starrailexpress.content.task;

/**
 * 任务点类型 → RGB 颜色。
 *
 * <p>这是任务点颜色的唯一来源：客户端任务透视（{@code TaskBlockOverlayRenderer}）与
 * 服务端指令（{@code /tmm:highlightTasks}）都读它，避免两边颜色漂移。
 *
 * <p>刻意只用 {@code int} 而不引用 {@code java.awt.Color}：专用服务端也会加载本类，
 * 不应依赖桌面模块。
 */
public final class TaskPointColors {
    /** 未收录的类型。 */
    public static final int UNDEFINED = -1;

    /** 空容器（未放物品的食物盘/饮料盘、未放书的讲台）的专用标记色。 */
    public static final int EMPTY_CONTAINER = 0xB266FF;

    private TaskPointColors() {
    }

    /**
     * @param type 任务点类型（{@code MapScanner} 写入 {@code GameUtils.taskBlocks} 的整数）
     * @return {@code 0xRRGGBB}；未收录的类型返回 {@link #UNDEFINED}
     */
    public static int rgb(int type) {
        return switch (type) {
            case 1 -> 0x00FF00; // 食物盘
            case 2 -> 0xEA5858; // 饮料盘
            case 3 -> 0x8DEABD; // 花洒
            case 4 -> 0x00FFDC; // 床
            case 5 -> 0xFFF200; // 跑步机
            case 6 -> 0xFF7F27; // 讲台
            case 7 -> 0xFFF79B; // 门（与拿钥匙时的同房间门透视同色）
            case 8 -> 0xFFAEC9; // 马桶
            case 9 -> 0x7EFFE4; // 座椅
            case 10 -> 0x7994FF; // 音符盒
            case 11 -> 0xFFAEC9; // 自动售货机
            case 16 -> 0xFFA500; // 炉灶
            case 17 -> 0xC0C0C0; // 灰尘
            case 18 -> 0x009632; // 运输起点
            case 19 -> 0xB42828; // 运输终点
            case 20 -> 0xFFFFB4; // 雕像
            case 21 -> 0xADFF2F; // 灌木
            case 22 -> 0xDAA520; // 草垫
            case 24 -> 0xFFD700; // 钟
            case 25 -> 0x87CEEB; // 射击练习
            case 28 -> 0xFF69B4; // FUMO
            default -> UNDEFINED;
        };
    }
}
