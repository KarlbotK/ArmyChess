package client;

import common.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class GamePanel extends JPanel {
    private Piece[][] board = new Piece[17][17];
    private ClientMain mainFrame;
    private Location selected;

    // 动态尺寸
    private int S = 30;
    private int OFF_X = 50;
    private int OFF_Y = 50;

    private static final Color BG_COLOR = new Color(45, 75, 45);
    private static final Color RAIL_COLOR = new Color(120, 220, 255);
    private static final Color ROAD_COLOR = new Color(120, 160, 120);
    private static final Color[] PLAYER_COLORS = {
            new Color(76, 175, 80), new Color(156, 39, 176), new Color(255, 152, 0), new Color(33, 150, 243)
    };

    public GamePanel(ClientMain frame) {
        this.mainFrame = frame;
        setBackground(BG_COLOR);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 1. 获取点击的【本地屏幕网格坐标】
                updateMetrics();
                int lx = (e.getX() - OFF_X + S/2) / S;
                int ly = (e.getY() - OFF_Y + S/2) / S;

                // 2. --- 核心修复：转换为【服务端绝对坐标】 ---
                // 如果不转，玩家2点下方会被当成玩家1的地盘，导致无法操作
                Point serverPt = localToServer(lx, ly);
                int sx = serverPt.x;
                int sy = serverPt.y;

                // 3. 后续逻辑全部使用 serverPt (sx, sy)
                if (!BoardConfig.isValidPoint(sx, sy)) return;

                if (!mainFrame.isSetupMode()) {
                    if (selected != null && !BoardConfig.isStation(sx, sy)) {
                        selected = null; repaint(); return;
                    }
                }

                if (mainFrame.isSetupMode()) handleSetupClick(sx, sy);
                else handleGameClick(sx, sy);
            }
        });
    }

    private void updateMetrics() {
        int w = getWidth(); int h = getHeight();
        if (w == 0 || h == 0) return;
        int minSide = Math.min(w, h);
        S = minSide / 19;
        if (S < 20) S = 20;
        OFF_X = (w - 17 * S) / 2;
        OFF_Y = (h - 17 * S) / 2;
    }

    public void initMyPieces(int pid) {
        for(int y=0; y<17; y++) { for(int x=0; x<17; x++) { if(BoardConfig.getTerritoryOwner(x,y)==pid) board[y][x]=null; } }
        List<Point> emptySlots = new ArrayList<>();
        int startX=0, endX=0, startY=0, endY=0;
        if (pid == 0) { startX=6; endX=10; startY=11; endY=16; }
        else if (pid == 1) { startX=0; endX=5; startY=6; endY=10; }
        else if (pid == 2) { startX=6; endX=10; startY=0; endY=5; }
        else if (pid == 3) { startX=11; endX=16; startY=6; endY=10; }
        for(int y=startY; y<=endY; y++) {
            for(int x=startX; x<=endX; x++) {
                if (BoardConfig.getTerritoryOwner(x, y) == pid && !BoardConfig.isCamp(x, y)) emptySlots.add(new Point(x, y));
            }
        }
        Collections.shuffle(emptySlots);
        Piece flag = new Piece(Piece.Type.FLAG, pid);
        List<Piece> landmines = new ArrayList<>(); for(int i=0;i<3;i++) landmines.add(new Piece(Piece.Type.LANDMINE, pid));
        List<Piece> bombs = new ArrayList<>(); for(int i=0;i<2;i++) bombs.add(new Piece(Piece.Type.BOMB, pid));
        List<Piece> others = new ArrayList<>();
        others.add(new Piece(Piece.Type.MARSHAL, pid)); others.add(new Piece(Piece.Type.GENERAL, pid));
        for(int i=0;i<2;i++) others.add(new Piece(Piece.Type.M_GENERAL, pid));
        for(int i=0;i<2;i++) others.add(new Piece(Piece.Type.BRIGADIER, pid));
        for(int i=0;i<2;i++) others.add(new Piece(Piece.Type.COLONEL, pid));
        for(int i=0;i<2;i++) others.add(new Piece(Piece.Type.MAJOR, pid));
        for(int i=0;i<3;i++) others.add(new Piece(Piece.Type.CAPTAIN, pid));
        for(int i=0;i<3;i++) others.add(new Piece(Piece.Type.LIEUTENANT, pid));
        for(int i=0;i<3;i++) others.add(new Piece(Piece.Type.SAPPER, pid));
        Collections.shuffle(others);
        fillPiece(flag, emptySlots, pid, (x, y) -> BoardConfig.isHQ(x, y));
        for (Piece mine : landmines) fillPiece(mine, emptySlots, pid, (x, y) -> BoardConfig.isLastTwoRows(pid, x, y));
        for (Piece bomb : bombs) fillPiece(bomb, emptySlots, pid, (x, y) -> !BoardConfig.isFirstRow(pid, x, y));
        for (Piece p : others) fillPiece(p, emptySlots, pid, (x, y) -> true);
        repaint();
    }
    private void fillPiece(Piece p, List<Point> slots, int pid, BiPredicate condition) {
        Iterator<Point> it = slots.iterator();
        while (it.hasNext()) { Point pt = it.next(); if (condition.test(pt.x, pt.y)) { board[pt.y][pt.x] = p; it.remove(); return; } }
    }
    interface BiPredicate { boolean test(int x, int y); }

    public void updateBoard(Piece[][] serverBoard) {
        if (mainFrame.isSetupMode()) {
            int myId = mainFrame.getMyId();
            for(int y=0; y<17; y++) { for(int x=0; x<17; x++) { if(BoardConfig.getTerritoryOwner(x,y)!=myId) this.board[y][x] = serverBoard[y][x]; } }
        } else { this.board = serverBoard; }
        repaint();
    }
    public Piece[][] getLocalBoard() { return board; }

    private void handleSetupClick(int x, int y) {
        if (BoardConfig.getTerritoryOwner(x, y) != mainFrame.getMyId()) return;
        if (BoardConfig.isCamp(x, y)) return;
        if (selected == null) {
            if (board[y][x] != null) { selected = new Location(x, y); repaint(); }
        } else {
            if (x == selected.x && y == selected.y) { selected = null; repaint(); return; }
            Piece p1 = board[selected.y][selected.x];
            Piece p2 = board[y][x];
            board[selected.y][selected.x] = p2;
            board[y][x] = p1;
            selected = null;
            repaint();
        }
    }

    private void handleGameClick(int x, int y) {
        if (selected == null) {
            Piece p = board[y][x];
            if (p != null && p.getOwnerId() == mainFrame.getMyId()) {
                selected = new Location(x, y);
                repaint();
            }
        } else {
            if (x == selected.x && y == selected.y) { selected = null; repaint(); return; }
            mainFrame.sendMove(selected, new Location(x, y));
            selected = null;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        updateMetrics();
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawRoads(g2);
        drawRails(g2);
        drawNodes(g2);
        drawPieces(g2);
        if (selected != null) drawSelection(g2);
    }

    private void drawRoads(Graphics2D g2) {
        g2.setColor(ROAD_COLOR);
        g2.setStroke(new BasicStroke(Math.max(1, S/20)));
        for(int y=0; y<17; y++) {
            for(int x=0; x<17; x++) {
                if (!BoardConfig.isValidPoint(x, y)) continue;
                Point p1 = getPixel(x, y);
                if (BoardConfig.isValidPoint(x+1, y) && BoardConfig.isNeighbor(x, y, x+1, y)) {
                    Point p2 = getPixel(x+1, y);
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
                if (BoardConfig.isValidPoint(x, y+1) && BoardConfig.isNeighbor(x, y, x, y+1)) {
                    Point p2 = getPixel(x, y+1);
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
                if (BoardConfig.isCamp(x, y)) {
                    int r = S/4; g2.drawOval(p1.x - r, p1.y - r, r*2, r*2);
                }
            }
        }
    }

    private void drawRails(Graphics2D g2) {
        g2.setColor(RAIL_COLOR);
        float outerW = Math.max(3, S/6.0f);
        float innerW = Math.max(1, S/10.0f);
        g2.setStroke(new BasicStroke(outerW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(100, 200, 255, 100));
        drawRailLines(g2);
        g2.setStroke(new BasicStroke(innerW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(RAIL_COLOR);
        drawRailLines(g2);
        // drawRailTiesAll(g2); // 暂时不画枕木，保持简洁
    }

    private void drawRailLines(Graphics2D g2) {
        for(int y=0; y<17; y++) {
            for(int x=0; x<17; x++) {
                if (!BoardConfig.isRail(x, y)) continue;
                Point p1 = getPixel(x, y);
                if (BoardConfig.isRail(x+1, y) && BoardConfig.isNeighbor(x, y, x+1, y)) {
                    Point p2 = getPixel(x+1, y);
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
                if (BoardConfig.isRail(x, y+1) && BoardConfig.isNeighbor(x, y, x, y+1)) {
                    Point p2 = getPixel(x, y+1);
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }
        }
        g2.setStroke(new BasicStroke(Math.max(3, S/6.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // 弯道绘制：必须使用旋转后的角度
        drawRotatedCurve(g2, 6, 6, 180, -90);
        drawRotatedCurve(g2, 10, 6, 90, -90);
        drawRotatedCurve(g2, 10, 10, 0, -90);
        drawRotatedCurve(g2, 6, 10, 270, -90);
    }

    private void drawRotatedCurve(Graphics2D g2, int cx, int cy, int startAngle, int arcAngle) {
        int myId = mainFrame.getMyId();
        int rotation = 0;
        if (myId == 1) rotation = 90;
        else if (myId == 2) rotation = 180;
        else if (myId == 3) rotation = -90;

        Point cp = getPixel(cx, cy);
        int r = S; int d = r * 2;
        g2.drawArc(cp.x - r, cp.y - r, d, d, startAngle + rotation, arcAngle);
    }

    private void drawNodes(Graphics2D g2) {
        for(int y=0; y<17; y++) {
            for(int x=0; x<17; x++) {
                Point p = getPixel(x, y);
                if (BoardConfig.isCamp(x, y)) {
                    g2.setColor(new Color(80, 120, 80));
                    int r = S/2 + 2;
                    g2.fillOval(p.x - r/2, p.y - r/2, r, r);
                    g2.setColor(new Color(180, 200, 180));
                    g2.setStroke(new BasicStroke(2));
                    g2.drawOval(p.x - r/2, p.y - r/2, r, r);
                } else if (BoardConfig.isHQ(x, y)) {
                    g2.setColor(new Color(160, 110, 60));
                    g2.setStroke(new BasicStroke(3));
                    int r = S/3;
                    g2.drawRect(p.x-r, p.y-r, r*2, r*2);
                    g2.drawLine(p.x-r, p.y-r, p.x+r, p.y+r);
                    g2.drawLine(p.x-r, p.y+r, p.x+r, p.y-r);
                }
            }
        }
    }

    private void drawPieces(Graphics2D g2) {
        for(int y=0; y<17; y++) {
            for(int x=0; x<17; x++) {
                Piece p = board[y][x];
                if (p == null) continue;
                if (!BoardConfig.isValidPoint(x, y)) continue;
                Point center = getPixel(x, y);
                int w = S - 2;
                int h = (S - 4) / 2 * 2 + Math.max(6, S/4);
                int px = center.x - w/2;
                int py = center.y - h/2;
                Color baseColor = PLAYER_COLORS[p.getOwnerId()];
                GradientPaint gp = new GradientPaint(px, py, baseColor.brighter(), px + w, py + h, baseColor.darker());
                g2.setPaint(gp);
                g2.fillRoundRect(px, py, w, h, S/4, S/4);
                g2.setStroke(new BasicStroke(2));
                g2.setColor(new Color(255, 255, 255, 80));
                g2.drawRoundRect(px+2, py+2, w-4, h-4, S/5, S/5);
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(px, py, w, h, S/4, S/4);
                if (p.getType() == Piece.Type.UNKNOWN) {
                    g2.setColor(new Color(0, 0, 0, 40));
                    g2.fillRect(px+4, py+4, w-8, h-8);
                } else {
                    int fontSize = Math.max(10, S/2 - 2);
                    g2.setFont(new Font("Microsoft YaHei", Font.BOLD, fontSize));
                    String name = p.getType().name;
                    if (name.length() > 2) g2.setFont(new Font("Microsoft YaHei", Font.BOLD, fontSize - 4));
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = center.x - fm.stringWidth(name) / 2;
                    int ty = center.y + fm.getAscent() / 2 - 2;
                    g2.setColor(new Color(0,0,0,100));
                    g2.drawString(name, tx+1, ty+1);
                    if (p.getOwnerId() == 2) g2.setColor(Color.BLACK);
                    else g2.setColor(Color.WHITE);
                    g2.drawString(name, tx, ty);
                }
            }
        }
    }

    private void drawSelection(Graphics2D g2) {
        Point p = getPixel(selected.x, selected.y);
        g2.setColor(Color.YELLOW);
        g2.setStroke(new BasicStroke(3));
        int r = S/2 + 2;
        g2.drawRect(p.x - r, p.y - r - 2, r*2, r*2 + 4);
    }

    // --- 坐标转换工具 ---
    private Point serverToLocal(int sx, int sy) {
        int myId = mainFrame.getMyId();
        if (myId == -1) myId = 0;
        int N = 16;
        if (myId == 0) return new Point(sx, sy);
        else if (myId == 1) return new Point(sy, N - sx);
        else if (myId == 2) return new Point(N - sx, N - sy);
        else if (myId == 3) return new Point(N - sy, sx);
        return new Point(sx, sy);
    }

    private Point localToServer(int lx, int ly) {
        int myId = mainFrame.getMyId();
        if (myId == -1) myId = 0;
        int N = 16;
        if (myId == 0) return new Point(lx, ly);
        else if (myId == 1) return new Point(N - ly, lx);
        else if (myId == 2) return new Point(N - lx, N - ly);
        else if (myId == 3) return new Point(ly, N - lx);
        return new Point(lx, ly);
    }

    private Point getPixel(int sx, int sy) {
        Point local = serverToLocal(sx, sy);
        return new Point(OFF_X + local.x * S, OFF_Y + local.y * S);
    }
}