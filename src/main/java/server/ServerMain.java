package server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerMain {
    // ★★★ 使用 ConcurrentHashMap 安全地管理全局多个游戏房间！ ★★★
    public static ConcurrentHashMap<Integer, GameRoom> roomManager = new ConcurrentHashMap<>();
    // 使用 AtomicInteger 保证生成房间号时多线程绝对安全
    public static AtomicInteger roomIdGenerator = new AtomicInteger(1001);

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8888)) {
            System.out.println("服务端启动 (Port:8888)，等待玩家...");

            // 创建第一个默认房间并放进咱们的 ConcurrentHashMap 里
            int defaultRoomId = roomIdGenerator.getAndIncrement();
            GameRoom room = new GameRoom();
            roomManager.put(defaultRoomId, room);
            System.out.println("已创建默认房间，房间号：" + defaultRoomId);

            while (room.getPlayerCount() < 4) {
                Socket s = serverSocket.accept();// 阻塞等待，有人连才往下走
                PlayerHandler handler = new PlayerHandler(s, room, room.getPlayerCount());
                room.addPlayer(handler);
                new Thread(handler).start();
                System.out.println("玩家 " + (handler.getPlayerId()+1) + " 加入了房间 " + defaultRoomId);
            }

            System.out.println("人员齐备！正在同步状态...");
            try { Thread.sleep(1000); } catch (InterruptedException e) {}

            System.out.println("进入布局阶段");
            room.startLayoutPhase();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}