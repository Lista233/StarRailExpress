package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** 有界的步行 A*：四向、跨一格台阶，不斜穿墙、不越过深坑。纯逻辑便于无游戏环境测试。 */
public final class Error404Pathfinder {
    public static final int MAX_NODES = 4096;
    public static final int MAX_RANGE = 64;
    public record Cell(int x, int y, int z) {
        public Cell offset(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
    }
    public interface Grid {
        boolean standable(Cell cell);
        boolean clear(Cell cell);
    }
    private record Node(Cell cell, int cost, int score) { }
    private Error404Pathfinder() { }

    public static List<Cell> find(Grid grid, Cell start, Cell goal) {
        if (distance(start, goal) > MAX_RANGE || !grid.standable(goal)) return List.of();
        if (start.equals(goal)) return List.of(start);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::score));
        Map<Cell, Integer> costs = new HashMap<>();
        Map<Cell, Cell> parent = new HashMap<>();
        Set<Cell> visited = new HashSet<>();
        open.add(new Node(start, 0, distance(start, goal)));
        costs.put(start, 0);
        while (!open.isEmpty() && visited.size() < MAX_NODES) {
            Node node = open.poll();
            Cell current = node.cell();
            if (!visited.add(current)) continue;
            if (current.equals(goal)) {
                List<Cell> result = new ArrayList<>();
                for (Cell at = goal; at != null; at = parent.get(at)) result.add(at);
                Collections.reverse(result);
                return List.copyOf(result);
            }
            for (int direction = 0; direction < 4; direction++) {
                int dx = direction == 0 ? 1 : direction == 1 ? -1 : 0;
                int dz = direction == 2 ? 1 : direction == 3 ? -1 : 0;
                for (int dy : new int[] {0, 1, -1}) {
                    Cell next = current.offset(dx, dy, dz);
                    if (!grid.standable(next)) continue;
                    // 上台阶先上升，下台阶先横移，两处扫掠空间都必须可容纳玩家。
                    if (dy > 0 && !grid.clear(current.offset(0, 1, 0))) continue;
                    if (dy < 0 && !grid.clear(current.offset(dx, 0, dz))) continue;
                    if (distance(start, next) > MAX_RANGE || visited.contains(next)) continue;
                    int cost = node.cost() + 1 + Math.abs(dy);
                    if (cost >= costs.getOrDefault(next, Integer.MAX_VALUE)) continue;
                    costs.put(next, cost);
                    parent.put(next, current);
                    open.add(new Node(next, cost, cost + distance(next, goal)));
                    break;
                }
            }
        }
        return List.of();
    }

    private static int distance(Cell a, Cell b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
    }
}
