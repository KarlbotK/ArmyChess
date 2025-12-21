package common;

import java.util.HashSet;
import java.util.Set;

public class BoardConfig {
    // 棋盘总宽高 (虽然很多地方是空的)
    public static final int SIZE = 17;

    // 用 HashSet 存储特殊点，查询速度快 O(1)
    private static final Set<String> CAMPS = new HashSet<>(); // 行营
    private static final Set<String> RAILS = new HashSet<>(); // 铁路
    private static final Set<String> HQS = new HashSet<>();   // 大本营 (Headquarters)

    // --- 静态代码块：类加载时只运行一次，初始化地图数据 ---
    static {
        // 1. 初始化行营 (每个玩家5个，共20个)
        // 规律：在各自 6x5 的矩形里，中间交错的 5 个点

        // 北方 (Player 2)
        addCamp(7, 2); addCamp(9, 2); addCamp(8, 3); addCamp(7, 4); addCamp(9, 4);
        // 南方 (Player 0)
        addCamp(7, 12); addCamp(9, 12); addCamp(8, 13); addCamp(7, 14); addCamp(9, 14);
        // 西方 (Player 1)
        addCamp(2, 7); addCamp(2, 9); addCamp(3, 8); addCamp(4, 7); addCamp(4, 9);
        // 东方 (Player 3)
        addCamp(12, 7); addCamp(12, 9); addCamp(13, 8); addCamp(14, 7); addCamp(14, 9);

        // 2. 初始化大本营 (每个玩家2个，必须放军旗的位置)
        // 规律：在最后底线的第2和第4个位置
        addHQ(7, 0); addHQ(9, 0);   // 北
        addHQ(7, 16); addHQ(9, 16); // 南
        addHQ(0, 7); addHQ(0, 9);   // 西
        addHQ(16, 7); addHQ(16, 9); // 东

        // 3. 初始化铁路 (最复杂的部分)
        // 军旗的铁路包括：四家基地的"前线" + 侧边 + 后方倒数第二排 + 中间九宫格

        // A. 中央公共区域 (3横3竖)
        // 关键修正：不是填满，而是画网格线
        // 竖线: x=6, x=8, x=10 (覆盖 y: 6~10)
        // 注意：这里为了让 checkStraightRail 算法能一步步走过去，
        // 我们必须把线上的所有点(包括中间的连接点)都标记为 RAIL。
        // 例如 (6,7) 是 RAIL，这样才能从 (6,6) 走到 (6,8)。
        for(int y=6; y<=10; y++) {
            addRail(6, y);  // 第1列
            addRail(8, y);  // 第3列
            addRail(10, y); // 第5列
        }
        // 横线: y=6, y=8, y=10 (覆盖 x: 6~10)
        for(int x=6; x<=10; x++) {
            addRail(x, 6);  // 第1行
            addRail(x, 8);  // 第3行
            addRail(x, 10); // 第5行
        }

        // 此时，(7,7), (9,7), (7,9), (9,9) 这些点没有被 addRail，它们就是虚空。

        // B. 四家基地铁路环 (U型+倒数第二排)
        // 北 (Base X:6-10, Y:0-5)
        for(int x=6; x<=10; x++) addRail(x, 5); // 前线
        for(int y=1; y<=5; y++) { addRail(6, y); addRail(10, y); } // 侧边
        for(int x=6; x<=10; x++) addRail(x, 1); // 后方(倒数第二排)

        // 南 (Base X:6-10, Y:11-16)
        for(int x=6; x<=10; x++) addRail(x, 11);
        for(int y=11; y<=15; y++) { addRail(6, y); addRail(10, y); }
        for(int x=6; x<=10; x++) addRail(x, 15);

        // 西 (Base X:0-5, Y:6-10)
        for(int y=6; y<=10; y++) addRail(5, y);
        for(int x=1; x<=5; x++) { addRail(x, 6); addRail(x, 10); }
        for(int y=6; y<=10; y++) addRail(1, y);

        // 东 (Base X:11-16, Y:6-10)
        for(int y=6; y<=10; y++) addRail(11, y);
        for(int x=11; x<=15; x++) { addRail(x, 6); addRail(x, 10); }
        for(int y=6; y<=10; y++) addRail(15, y);
    }

    // 辅助添加方法
    private static void addCamp(int x, int y) { CAMPS.add(x + "," + y); }
    private static void addRail(int x, int y) { RAILS.add(x + "," + y); }
    private static void addHQ(int x, int y) { HQS.add(x + "," + y); }

    // --- 对外提供的判断方法 ---

    public static boolean isCamp(int x, int y) { return CAMPS.contains(x + "," + y); }
    public static boolean isRail(int x, int y) { return RAILS.contains(x + "," + y); }
    public static boolean isHQ(int x, int y) { return HQS.contains(x + "," + y); }

    // 判断某个坐标是否在棋盘有效区域内 (画图和点击检测用)
    public static boolean isValidPoint(int x, int y) {
        // 北南基地
        if (x >= 6 && x <= 10) {
            if (y >= 0 && y <= 5) return true;
            if (y >= 11 && y <= 16) return true;
        }
        // 东西基地
        if (y >= 6 && y <= 10) {
            if (x >= 0 && x <= 5) return true;
            if (x >= 11 && x <= 16) return true;
        }

        // 中间九宫格 (挖掉4个空洞)
        // 规则：只有 x=6,8,10 或 y=6,8,10 的线是有效的
        if (x >= 6 && x <= 10 && y >= 6 && y <= 10) {
            return isRail(x, y); // 只有 3横3竖 线上的点有效，(7,7)这种就是false
        }

        return false;
    }

    public static boolean isStation(int x, int y) {
        if (!isValidPoint(x, y)) return false;

        // 如果在中间区域 (Middle Area)
        if (x >= 6 && x <= 10 && y >= 6 && y <= 10) {
            // 只有交叉点是站点：x 和 y 都是偶数 (6, 8, 10)
            // 例如 (6,7) 是路不是站，(6,6) 是站
            return (x % 2 == 0) && (y % 2 == 0);
        }

        // 基地内部：所有有效点都是站点
        return true;
    }

    // --- 核心算法：判断两点是否物理相邻 (能否走一步到达) ---
    public static boolean isNeighbor(int x1, int y1, int x2, int y2) {
        if (!isValidPoint(x1, y1) || !isValidPoint(x2, y2)) return false;
        int dx = Math.abs(x1 - x2);
        int dy = Math.abs(y1 - y2);

        // 1. 坐标完全一样，不算邻居
        if (dx == 0 && dy == 0) return false;

        // 2. 行营特权：允许走斜线 (8个方向)
        // 只要其中一个是行营，且距离在1格之内(含斜向)
        if ((isCamp(x1, y1) || isCamp(x2, y2)) && dx <= 1 && dy <= 1) return true;

        // 3. 普通点：只允许走直线 (上下左右)
        if (dx + dy == 1) {
            // 这里有一个特殊规则：基地的偶数排(第2、4排)不能直接进中间区域
            // 需要切断连接，否则工兵能直接穿墙
            if (isBlockedPath(x1, y1, x2, y2)) return false;
            return true;
        }

        // 4. 四角弯道连接 (视觉上是弯的，逻辑上算相邻)
        if (isCornerConnected(x1, y1, x2, y2)) return true;

        return false;
    }

    // 阻断逻辑：切断基地死胡同与中间区域的连接,为了防止工兵从第一排第二、四列的位置直接飞出去
    private static boolean isBlockedPath(int x1, int y1, int x2, int y2) {
        // 东西向切断 (跨越 x=5|6 或 x=10|11)
        if (y1 == y2) {
            int minX = Math.min(x1, x2);
            if (minX == 5 || minX == 10) {
                // 如果是第2排(y=7) 或 第4排(y=9)，切断
                if (y1 == 7 || y1 == 9) return true;
            }
        }
        // 南北向切断 (跨越 y=5|6 或 y=10|11)
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            if (minY == 5 || minY == 10) {
                // 如果是第2列(x=7) 或 第4列(x=9)，切断
                if (x1 == 7 || x1 == 9) return true;
            }
        }
        return false;
    }

    // 检查是否是弯道连接
    public static boolean isCornerConnected(int x1, int y1, int x2, int y2) {
        // 西(5,6) <-> 北(6,5)
        if (checkCorner(x1, y1, x2, y2, 5, 6, 6, 5)) return true;
        // 北(10,5) <-> 东(11,6)
        if (checkCorner(x1, y1, x2, y2, 10, 5, 11, 6)) return true;
        // 东(11,10) <-> 南(10,11)
        if (checkCorner(x1, y1, x2, y2, 11, 10, 10, 11)) return true;
        // 南(6,11) <-> 西(5,10)
        if (checkCorner(x1, y1, x2, y2, 6, 11, 5, 10)) return true;
        return false;
    }

    private static boolean checkCorner(int x1, int y1, int x2, int y2, int tx1, int ty1, int tx2, int ty2) {
        return (x1 == tx1 && y1 == ty1 && x2 == tx2 && y2 == ty2) ||
                (x1 == tx2 && y1 == ty2 && x2 == tx1 && y2 == ty1);
    }

    // 获取某坐标属于哪个玩家 (用于布局校验)
    public static int getTerritoryOwner(int x, int y) {
        if (y >= 11 && y <= 16 && x >= 6 && x <= 10) return 0; // 南
        if (x >= 0 && x <= 5 && y >= 6 && y <= 10) return 1;   // 西
        if (y >= 0 && y <= 5 && x >= 6 && x <= 10) return 2;   // 北
        if (x >= 11 && x <= 16 && y >= 6 && y <= 10) return 3; // 东
        return -1; // 中间公共区
    }

    // 辅助规则判断
    public static boolean isLastTwoRows(int pid, int x, int y) {
        if (pid == 0) return y >= 15;
        if (pid == 2) return y <= 1;
        if (pid == 1) return x <= 1;
        if (pid == 3) return x >= 15;
        return false;
    }

    public static boolean isFirstRow(int pid, int x, int y) {
        if (pid == 0) return y == 11;
        if (pid == 2) return y == 5;
        if (pid == 1) return x == 5;
        if (pid == 3) return x == 11;
        return false;
    }

    public static boolean isTeammate(int p1, int p2) {
        return (p1 % 2) == (p2 % 2); // 0和2一队，1和3一队
    }
}