package server;

import common.NetMsg;
import java.io.*;
import java.net.Socket;

public class PlayerHandler implements Runnable {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private GameRoom room;
    private int playerId;

    public boolean isAlive = true;
    public int timeoutCount = 0;
    public boolean hasSubmittedLayout = false;

    public PlayerHandler(Socket socket, GameRoom room, int playerId) {
        this.socket = socket;
        this.room = room;
        this.playerId = playerId;
    }

    public int getPlayerId() { return playerId; }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            send(new NetMsg(NetMsg.Type.LOGIN_SUCCESS, playerId, -1));

            while (true) {
                NetMsg msg = (NetMsg) in.readObject();
                switch (msg.type) {
                    case SUBMIT_LAYOUT:
                        room.handleLayoutSubmit(playerId, (common.Piece[][]) msg.data);
                        break;
                    case MOVE_REQ:
                        common.Location[] locs = (common.Location[]) msg.data;
                        room.handleMove(playerId, locs[0], locs[1]);
                        break;
                    case SURRENDER:
                        room.handleSurrender(playerId);
                        break;
                    // --- 新增：处理聊天请求 ---
                    case CHAT:
                        room.handleChat(playerId, (String) msg.data);
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("玩家 " + (playerId+1) + " 断开连接 ");
            room.handleSurrender(playerId);
        }
    }

    public synchronized void send(NetMsg msg) {
        try {
            if (socket == null || socket.isClosed()) return;

            out.writeObject(msg);
            out.reset();
            out.flush();
        } catch (IOException e) {
            //e.printStackTrace();
            try { socket.close(); } catch (IOException ex) {}
        }
    }
}
