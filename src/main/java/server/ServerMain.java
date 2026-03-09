package server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerMain {
    // 1. 全局安全的房间管理器
    public static ConcurrentHashMap<Integer, GameRoom> roomManager = new ConcurrentHashMap<>();
    // 2. 绝对并发安全的房号生成器
    public static AtomicInteger roomIdGenerator = new AtomicInteger(1001);
    // 3. 招募全职老兵：固定大小的线程池
    public static ExecutorService threadPool = Executors.newFixedThreadPool(200);

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8888)) {
            System.out.println("军棋 Server 启动，Port:8888...");

            // 准备第一张拼桌
            GameRoom currentRoom = new GameRoom();
            roomManager.put(roomIdGenerator.getAndIncrement(), currentRoom);

            // 迎宾小哥（主线程）死循环接客，永不下班！
            while (true) {
                Socket s = serverSocket.accept(); // 阻塞等待玩家连接

                PlayerHandler handler = new PlayerHandler(s, currentRoom, currentRoom.getPlayerCount());
                currentRoom.addPlayer(handler);

                // ★ 拒绝 new Thread()，把连接交给线程池里的老兵去处理！
                threadPool.submit(handler);

                // 如果这桌凑够 4 个人了
                if (currentRoom.getPlayerCount() == 4) {
                    currentRoom.startLayoutPhase(); // 让这桌人自己玩
                    // ★ 核心：马上搬出一张新桌子，接待下一批客人
                    currentRoom = new GameRoom();
                    roomManager.put(roomIdGenerator.getAndIncrement(), currentRoom);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}