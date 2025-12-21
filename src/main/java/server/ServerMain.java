package server;

import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8888)) {
            System.out.println("服务端启动 (Port:8888)，等待4人...");
            GameRoom room = new GameRoom();

            while (room.getPlayerCount() < 4) {
                Socket s = serverSocket.accept();// 阻塞等待，有人连才往下走
                // 这里的 playerId 分配顺序是 1, 2, 3, 4
                PlayerHandler handler = new PlayerHandler(s, room, room.getPlayerCount());
                room.addPlayer(handler);

                // 启动玩家线程
                new Thread(handler).start();// 开启新线程
                System.out.println("玩家 " + (handler.getPlayerId()+1) + " 连接成功");
            }

            System.out.println("人员齐备！正在同步状态...");

            // 等待1秒，确保玩家4的 ObjectOutputStream 初始化完毕
            try { Thread.sleep(1000); } catch (InterruptedException e) {}

            System.out.println("进入布局阶段");
            room.startLayoutPhase();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}