package client;

import common.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.Socket;

public class ClientMain extends JFrame {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private GamePanel gamePanel;
    private JLabel statusLabel;
    private JButton surrenderBtn;
    private JButton submitLayoutBtn;

    private JTextArea chatArea;
    private JTextField chatInput;
    private JButton sendBtn;

    private int myId = -1;
    private boolean isSetupMode = false;
    private Timer countdownTimer;
    private int currentCountDown = 30;

    public ClientMain() {
        setTitle("四国军旗 - Java Client");
        setSize(950, 1000);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        statusLabel = new JLabel("连接中...");
        statusLabel.setFont(new Font("SimHei", Font.PLAIN, 16));
        submitLayoutBtn = new JButton("提交布局");
        surrenderBtn = new JButton("投降");

        submitLayoutBtn.setEnabled(false);
        surrenderBtn.setEnabled(false);

        submitLayoutBtn.addActionListener(e -> doSubmitLayout());
        surrenderBtn.addActionListener(e -> sendMsg(new NetMsg(NetMsg.Type.SURRENDER, null, myId)));

        topPanel.add(statusLabel);
        topPanel.add(submitLayoutBtn);
        topPanel.add(surrenderBtn);
        add(topPanel, BorderLayout.NORTH);

        gamePanel = new GamePanel(this);
        add(gamePanel, BorderLayout.CENTER);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setPreferredSize(new Dimension(800, 150));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setFont(new Font("SimHei", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JPanel inputPanel = new JPanel(new BorderLayout());
        chatInput = new JTextField();
        chatInput.setFont(new Font("SimHei", Font.PLAIN, 14));
        sendBtn = new JButton("发送");

        Runnable doSend = () -> {
            String txt = chatInput.getText().trim();
            if(!txt.isEmpty()) {
                sendMsg(new NetMsg(NetMsg.Type.CHAT, txt, myId));
                chatInput.setText("");
            }
        };
        sendBtn.addActionListener(e -> doSend.run());
        chatInput.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) { if(e.getKeyCode()==KeyEvent.VK_ENTER) doSend.run(); }
        });

        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        chatPanel.add(scrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        add(chatPanel, BorderLayout.SOUTH);

        setVisible(true);
        connect();
    }

    private void connect() {
        try {
            String serverIp = "127.0.0.1";
            // String serverIp = JOptionPane.showInputDialog(this, "请输入服务器IP:", "127.0.0.1");
            if(serverIp == null) System.exit(0);

            socket = new Socket(serverIp, 8888);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            new Thread(this::readLoop).start();
        } catch (Exception e) {
            statusLabel.setText("连接失败！请检查服务端");
        }
    }

    private void readLoop() {
        try {
            while (true) {
                NetMsg msg = (NetMsg) in.readObject();
                handleMsg(msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> appendChat("[系统] 与服务器断开连接"));
        }
    }

    private void handleMsg(NetMsg msg) {
        SwingUtilities.invokeLater(() -> {
            switch (msg.type) {
                case LOGIN_SUCCESS:
                    myId = (int) msg.data;
                    setTitle("我是玩家: " + (myId+1) + " (队伍 " + (myId%2==0?"A":"B") + ")");
                    appendChat("[系统] 登录成功，我是玩家 " + (myId+1));
                    break;
                case START_LAYOUT:
                    isSetupMode = true;
                    submitLayoutBtn.setEnabled(true);
                    statusLabel.setText("请调整布局，然后点击提交");
                    if(countdownTimer != null) countdownTimer.stop();
                    statusLabel.setForeground(Color.BLACK);
                    gamePanel.initMyPieces(myId);
                    appendChat("[系统] 游戏即将开始，请布置阵型！");
                    break;
                case SUBMIT_LAYOUT: break;
                case LAYOUT_REJECT:
                    submitLayoutBtn.setEnabled(true);
                    isSetupMode = true;
                    JOptionPane.showMessageDialog(this, "布局不合规，请修改后重新提交！");
                    break;
                case GAME_START:
                    isSetupMode = false;
                    submitLayoutBtn.setEnabled(false);
                    surrenderBtn.setEnabled(true);
                    statusLabel.setText("游戏开始！");
                    appendChat("[系统] 全员准备就绪，游戏开始！");
                    break;
                case UPDATE_BOARD:
                    gamePanel.updateBoard((Piece[][]) msg.data);
                    break;
                case TURN_NOTIFY:
                    int totalTime = (int) msg.data;
                    int turnId = msg.playerId;
                    startCountdown(turnId, totalTime);
                    break;
                case MSG_TEXT:
                    appendChat("[系统] " + msg.data);
                    break;
                case CHAT:
                    appendChat((String) msg.data);
                    break;
                // --- 核心修复：处理游戏结束弹窗 ---
                case GAME_OVER:
                    String winMsg = (String) msg.data;
                    // 1. 写日志
                    appendChat("[系统] " + winMsg);
                    // 2. 弹窗
                    JOptionPane.showMessageDialog(this, winMsg, "战斗结束", JOptionPane.INFORMATION_MESSAGE);

                    // 3. 停止倒计时，清理UI状态
                    if(countdownTimer != null) countdownTimer.stop();
                    statusLabel.setText("游戏已结束");
                    statusLabel.setForeground(Color.RED);
                    surrenderBtn.setEnabled(false);
                    break;
            }
        });
    }

    private void appendChat(String text) {
        chatArea.append(text + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void startCountdown(int turnPid, int seconds) {
        if (countdownTimer != null) countdownTimer.stop();
        currentCountDown = seconds;
        statusLabel.setForeground(Color.BLACK);
        statusLabel.setFont(new Font("SimHei", Font.PLAIN, 16));
        updateStatusText(turnPid);

        countdownTimer = new Timer(1000, e -> {
            currentCountDown--;
            if (currentCountDown <= 10) {
                statusLabel.setForeground(Color.RED);
                statusLabel.setFont(new Font("SimHei", Font.BOLD, 22));
            }
            updateStatusText(turnPid);
            if (currentCountDown <= 0) ((Timer)e.getSource()).stop();
        });
        countdownTimer.start();
    }

    private void updateStatusText(int turnPid) {
        String who = (turnPid == myId) ? "你的回合" : ("等待玩家 " + (turnPid+1));
        statusLabel.setText(String.format("%s ... %ds", who, currentCountDown));
    }

    private void doSubmitLayout() {
        Piece[][] layout = gamePanel.getLocalBoard();
        sendMsg(new NetMsg(NetMsg.Type.SUBMIT_LAYOUT, layout, myId));
        submitLayoutBtn.setEnabled(false);
        statusLabel.setText("已提交，等待其他人...");
    }

    public void sendMove(Location from, Location to) {
        if (isSetupMode) return;
        sendMsg(new NetMsg(NetMsg.Type.MOVE_REQ, new Location[]{from, to}, myId));
    }

    private void sendMsg(NetMsg msg) {
        try {
            out.writeObject(msg);
            out.reset();
            out.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }

    public int getMyId() { return myId; }
    public boolean isSetupMode() { return isSetupMode; }

    public static void main(String[] args) {
        new ClientMain();
    }
}