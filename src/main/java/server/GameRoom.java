package server;

import common.*;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;

public class GameRoom {
    private List<PlayerHandler> players = new ArrayList<>();
    private Piece[][] board = new Piece[17][17];
    private int currentTurn = 0;
    private boolean isGameStarted = false;
    private Timer turnTimer = new Timer();
    private TimerTask currentTask;

    public void addPlayer(PlayerHandler p) { players.add(p); }
    public int getPlayerCount() { return players.size(); }

    public void startLayoutPhase() {
        broadcast(new NetMsg(NetMsg.Type.START_LAYOUT, null, -1));
    }

    public synchronized void handleLayoutSubmit(int pid, Piece[][] subBoard) {
        PlayerHandler p = players.get(pid);
        if (p.hasSubmittedLayout) return;

        for(int y=0; y<17; y++) {
            for(int x=0; x<17; x++) {
                Piece piece = subBoard[y][x];
                if(piece != null) {
                    if (BoardConfig.getTerritoryOwner(x, y) != pid) continue;
                    if (piece.getType() == Piece.Type.FLAG && !BoardConfig.isHQ(x, y)) {
                        sendReject(p, "错误：军旗必须在大本营！"); return;
                    }
                    if (piece.getType() == Piece.Type.LANDMINE && !BoardConfig.isLastTwoRows(pid, x, y)) {
                        sendReject(p, "错误：地雷只能在最后两排！"); return;
                    }
                    if (piece.getType() == Piece.Type.BOMB && BoardConfig.isFirstRow(pid, x, y)) {
                        sendReject(p, "错误：炸弹不能放在第一排！"); return;
                    }
                    if (BoardConfig.isCamp(x, y)) {
                        sendReject(p, "错误：行营必须为空！"); return;
                    }
                    board[y][x] = piece;
                }
            }
        }

        p.hasSubmittedLayout = true;
        broadcast(new NetMsg(NetMsg.Type.MSG_TEXT, "玩家 " + (pid + 1) + " 布局完成", -1));
        broadcastBoard();

        boolean allReady = players.size() == 4;
        for(PlayerHandler ph : players) if(!ph.hasSubmittedLayout) allReady = false;

        if(allReady) {
            isGameStarted = true;
            System.out.println(">>> 所有玩家布局完成，游戏正式开始！");
            broadcast(new NetMsg(NetMsg.Type.GAME_START, null, -1));
            broadcastBoard();
            startTurnLogic();
        }
    }

    private void sendReject(PlayerHandler p, String msg) {
        p.send(new NetMsg(NetMsg.Type.MSG_TEXT, msg, -1));
        p.send(new NetMsg(NetMsg.Type.LAYOUT_REJECT, null, -1));
    }

    public void handleChat(int pid, String content) {
        String fullMsg = "玩家 " + (pid + 1) + ": " + content;
        broadcast(new NetMsg(NetMsg.Type.CHAT, fullMsg, pid));
    }

    private void startTurnLogic() {
        if (currentTask != null) currentTask.cancel();
        broadcast(new NetMsg(NetMsg.Type.TURN_NOTIFY, 30, currentTurn));
        currentTask = new TimerTask() { @Override public void run() { handleTimeout(); } };
        turnTimer.schedule(currentTask, 30000);
    }
    private synchronized void handleTimeout() {
        PlayerHandler p = players.get(currentTurn);
        p.timeoutCount++;
        broadcast(new NetMsg(NetMsg.Type.MSG_TEXT, "玩家 " + (currentTurn + 1) + " 超时", -1));
        if (p.timeoutCount >= 5) handleSurrender(currentTurn); else nextTurn();
    }
    private void nextTurn() {
        int loop = 0;
        do { currentTurn = (currentTurn + 1) % 4; loop++; } while (!players.get(currentTurn).isAlive && loop < 4);
        if (!players.get(currentTurn).isAlive) return;
        if (!hasLegalMove(currentTurn)) {
            System.out.println(">>> 玩家 " + (currentTurn + 1) + " 无棋可走，判负！");
            broadcast(new NetMsg(NetMsg.Type.MSG_TEXT, "玩家 " + (currentTurn + 1) + " 无棋可走，判负！", -1));
            handleSurrender(currentTurn);
            return;
        }
        startTurnLogic();
    }

    private boolean hasLegalMove(int pid) {
        for (int y = 0; y < 17; y++) {
            for (int x = 0; x < 17; x++) {
                Piece p = board[y][x];
                if (p == null || p.getOwnerId() != pid) continue;
                if (p.getType() == Piece.Type.LANDMINE) continue;
                if (p.getType() == Piece.Type.FLAG) continue;
                if (BoardConfig.isHQ(x, y)) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int tx = x + dx; int ty = y + dy;
                        if (!BoardConfig.isValidPoint(tx, ty)) continue;
                        if (!BoardConfig.isNeighbor(x, y, tx, ty)) continue;
                        Piece target = board[ty][tx];
                        if (target == null) return true;
                        else if (target.getOwnerId() != pid) {
                            if (!BoardConfig.isTeammate(pid, target.getOwnerId()) && !BoardConfig.isCamp(tx, ty)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public synchronized void handleSurrender(int pid) {
        PlayerHandler p = players.get(pid);
        if (!p.isAlive) return;
        p.isAlive = false;
        for(int y=0; y<17; y++) {
            for(int x=0; x<17; x++) { if(board[y][x] != null && board[y][x].getOwnerId() == pid) board[y][x] = null; }
        }
        if (isGameStarted) {
            broadcastBoard();
            broadcast(new NetMsg(NetMsg.Type.MSG_TEXT, "玩家 " + (pid + 1) + " 投降/出局！", -1));
            checkWinner();
            if (isGameStarted) nextTurn();
        } else {
            System.out.println(">>> 玩家 " + (pid + 1) + " 在结算阶段断开连接");
        }
    }
    private void checkWinner() {
        if (!isGameStarted) return;
        boolean team0Alive = players.get(0).isAlive || players.get(2).isAlive;
        boolean team1Alive = players.get(1).isAlive || players.get(3).isAlive;
        if (!team0Alive || !team1Alive) {
            String winner = team0Alive ? "南北队 (1 & 3)" : "东西队 (2 & 4)";
            if (!team0Alive && !team1Alive) winner = "无（平局）";
            System.out.println(">>> 游戏结束！获胜方：" + winner);
            broadcast(new NetMsg(NetMsg.Type.GAME_OVER, "游戏结束！\n\n🎉 恭喜 " + winner + " 获得胜利！🎉", -1));
            shutdownServer();
        }
    }
    private void shutdownServer() {
        isGameStarted = false;
        if (currentTask != null) currentTask.cancel();
        new Thread(() -> {
            try {
                System.out.println(">>> 服务器将在 5 秒后自动关闭...");
                Thread.sleep(5000);
                System.out.println(">>> 服务器正在关闭，再见！");
                System.exit(0);
            } catch (InterruptedException e) { e.printStackTrace(); }
        }).start();
    }
    private void revealFlag(int ownerId) {
        boolean found = false;
        for(int y=0; y<17; y++) {
            for(int x=0; x<17; x++) {
                Piece p = board[y][x];
                if (p != null && p.getOwnerId() == ownerId && p.getType() == Piece.Type.FLAG) {
                    p.setRevealed(true); found = true;
                }
            }
        }
        if (found) broadcast(new NetMsg(NetMsg.Type.MSG_TEXT, "【系统】玩家 " + (ownerId + 1) + " 司令阵亡，军旗亮出！", -1));
    }
    public synchronized void handleMove(int pid, Location from, Location to) {
        if (!isGameStarted || pid != currentTurn) return;
        Piece attacker = board[from.y][from.x];
        if (attacker == null || attacker.getOwnerId() != pid) return;
        if (attacker.getType() == Piece.Type.LANDMINE) return;
        if (BoardConfig.isHQ(from.x, from.y)) {
            players.get(pid).send(new NetMsg(NetMsg.Type.MSG_TEXT, "大本营内的棋子不能移动！", -1)); return;
        }
        if (!BoardConfig.isStation(to.x, to.y)) {
            players.get(pid).send(new NetMsg(NetMsg.Type.MSG_TEXT, "这里是铁轨，不能停靠！", -1)); return;
        }
        if (!isValidPath(attacker, from, to)) {
            players.get(pid).send(new NetMsg(NetMsg.Type.MSG_TEXT, "路径不通！", -1)); return;
        }
        Piece defender = board[to.y][to.x];
        if (defender == null) {
            board[to.y][to.x] = attacker; board[from.y][from.x] = null;
        } else {
            if (BoardConfig.isTeammate(attacker.getOwnerId(), defender.getOwnerId())) return;
            if (BoardConfig.isCamp(to.x, to.y)) {
                players.get(pid).send(new NetMsg(NetMsg.Type.MSG_TEXT, "行营免战！", -1)); return;
            }
            int res = judgeBattle(attacker, defender);
            if (res == 1) {
                if (defender.getType() == Piece.Type.MARSHAL) revealFlag(defender.getOwnerId());
                if (defender.getType() == Piece.Type.FLAG) {
                    board[to.y][to.x] = attacker; board[from.y][from.x] = null;
                    handleSurrender(defender.getOwnerId()); return;
                }
                board[to.y][to.x] = attacker; board[from.y][from.x] = null;
            } else if (res == 0) {
                if (attacker.getType() == Piece.Type.MARSHAL) revealFlag(attacker.getOwnerId());
                if (defender.getType() == Piece.Type.MARSHAL) revealFlag(defender.getOwnerId());
                board[to.y][to.x] = null; board[from.y][from.x] = null;
            } else {
                if (attacker.getType() == Piece.Type.MARSHAL) revealFlag(attacker.getOwnerId());
                board[from.y][from.x] = null;
            }
        }
        if (currentTask != null) currentTask.cancel();
        broadcastBoard();
        nextTurn();
    }
    private boolean isValidPath(Piece p, Location from, Location to) {
        if (!BoardConfig.isValidPoint(to.x, to.y)) return false;
        if (BoardConfig.isNeighbor(from.x, from.y, to.x, to.y)) return true;
        if (BoardConfig.isRail(from.x, from.y) && BoardConfig.isRail(to.x, to.y)) {
            if (p.getType() == Piece.Type.SAPPER) return bfsRail(from, to);
            return checkStraightLine(from, to) || checkCurveLine(from, to);
        }
        return false;
    }
    private boolean checkStraightLine(Location from, Location to) {
        if (from.x != to.x && from.y != to.y) return false;
        return isPathClear(from, to);
    }
    private boolean checkCurveLine(Location from, Location to) {
        if (isNorthRight(from) && isEastTop(to)) return isPathClear(from, new Location(10,5)) && isPathClear(new Location(11,6), to);
        if (isEastTop(from) && isNorthRight(to)) return isPathClear(from, new Location(11,6)) && isPathClear(new Location(10,5), to);
        if (isEastBottom(from) && isSouthRight(to)) return isPathClear(from, new Location(11,10)) && isPathClear(new Location(10,11), to);
        if (isSouthRight(from) && isEastBottom(to)) return isPathClear(from, new Location(10,11)) && isPathClear(new Location(11,10), to);
        if (isSouthLeft(from) && isWestBottom(to)) return isPathClear(from, new Location(6,11)) && isPathClear(new Location(5,10), to);
        if (isWestBottom(from) && isSouthLeft(to)) return isPathClear(from, new Location(5,10)) && isPathClear(new Location(6,11), to);
        if (isWestTop(from) && isNorthLeft(to)) return isPathClear(from, new Location(5,6)) && isPathClear(new Location(6,5), to);
        if (isNorthLeft(from) && isWestTop(to)) return isPathClear(from, new Location(6,5)) && isPathClear(new Location(5,6), to);
        return false;
    }
    private boolean isNorthRight(Location l) { return l.x == 10 && l.y <= 5; }
    private boolean isEastTop(Location l) { return l.y == 6 && l.x >= 11; }
    private boolean isEastBottom(Location l) { return l.y == 10 && l.x >= 11; }
    private boolean isSouthRight(Location l) { return l.x == 10 && l.y >= 11; }
    private boolean isSouthLeft(Location l) { return l.x == 6 && l.y >= 11; }
    private boolean isWestBottom(Location l) { return l.y == 10 && l.x <= 5; }
    private boolean isWestTop(Location l) { return l.y == 6 && l.x <= 5; }
    private boolean isNorthLeft(Location l) { return l.x == 6 && l.y <= 5; }
    private boolean isPathClear(Location from, Location to) {
        if (BoardConfig.isCornerConnected(from.x, from.y, to.x, to.y)) return true;
        int dx = Integer.compare(to.x, from.x);
        int dy = Integer.compare(to.y, from.y);
        int cx = from.x + dx;
        int cy = from.y + dy;
        while (cx != to.x || cy != to.y) {
            if (!BoardConfig.isRail(cx, cy)) return false;
            if (board[cy][cx] != null) return false;
            cx += dx;
            cy += dy;
        }
        return true;
    }

    // --- 核心修复：工兵BFS增加连通性（墙壁）检查 ---
    private boolean bfsRail(Location start, Location end) {
        Queue<Location> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        visited.add(start.x + "," + start.y);
        int[][] dirs = {{0,1}, {0,-1}, {1,0}, {-1,0}};
        while(!queue.isEmpty()) {
            Location cur = queue.poll();
            if (cur.x == end.x && cur.y == end.y) return true;

            // 1. 直线邻居
            for(int[] d : dirs) {
                int nx = cur.x + d[0];
                int ny = cur.y + d[1];

                // 必须在地图内
                if (!BoardConfig.isValidPoint(nx, ny)) continue;
                // 必须是铁路
                if (!BoardConfig.isRail(nx, ny)) continue;
                // 【修复点】：必须物理连通（不能穿过第2/4排的墙）
                if (!BoardConfig.isNeighbor(cur.x, cur.y, nx, ny)) continue;

                checkAndAdd(nx, ny, end, visited, queue);
            }

            // 2. 弯道跳跃 (Corner Jump)
            checkCornerJump(cur.x, cur.y, 10, 5, 11, 6, end, visited, queue);
            checkCornerJump(cur.x, cur.y, 11, 6, 10, 5, end, visited, queue);
            checkCornerJump(cur.x, cur.y, 11, 10, 10, 11, end, visited, queue);
            checkCornerJump(cur.x, cur.y, 10, 11, 11, 10, end, visited, queue);
            checkCornerJump(cur.x, cur.y, 6, 11, 5, 10, end, visited, queue);
            checkCornerJump(cur.x, cur.y, 5, 10, 6, 11, end, visited, queue);
            checkCornerJump(cur.x, cur.y, 5, 6, 6, 5, end, visited, queue);
            checkCornerJump(cur.x, cur.y, 6, 5, 5, 6, end, visited, queue);
        }
        return false;
    }

    // 弯道跳跃辅助
    private void checkCornerJump(int cx, int cy, int srcX, int srcY, int destX, int destY,
                                 Location end, Set<String> visited, Queue<Location> queue) {
        if (cx == srcX && cy == srcY) {
            checkAndAdd(destX, destY, end, visited, queue);
        }
    }

    private void checkAndAdd(int nx, int ny, Location end, Set<String> visited, Queue<Location> queue) {
        String key = nx + "," + ny;
        if (visited.contains(key)) return;
        Piece obstacle = board[ny][nx];
        if (obstacle != null) {
            if (nx == end.x && ny == end.y) { visited.add(key); queue.add(new Location(nx, ny)); }
            return;
        }
        visited.add(key);
        queue.add(new Location(nx, ny));
    }
    // -------------------------------------------------------------------------

    private int judgeBattle(Piece atk, Piece def) {
        if (def.getType() == Piece.Type.FLAG) return 1;
        if (atk.getType() == Piece.Type.BOMB || def.getType() == Piece.Type.BOMB) return 0;
        if (def.getType() == Piece.Type.LANDMINE) return (atk.getType() == Piece.Type.SAPPER) ? 1 : -1;
        if (atk.getType().rank > def.getType().rank) return 1;
        if (atk.getType().rank == def.getType().rank) return 0;
        return -1;
    }
    private void broadcastBoard() {
        for (PlayerHandler p : players) {
            Piece[][] view = new Piece[17][17];
            for(int y=0; y<17; y++) {
                for(int x=0; x<17; x++) {
                    Piece real = board[y][x];
                    if (real != null) {
                        if (real.getOwnerId() == p.getPlayerId()) view[y][x] = real;
                        else view[y][x] = real.isRevealed() ? real : real.getMaskedCopy();
                    }
                }
            }
            p.send(new NetMsg(NetMsg.Type.UPDATE_BOARD, view, -1));
        }
    }
    private void broadcast(NetMsg msg) {
        for (PlayerHandler p : players) p.send(msg);
    }
}