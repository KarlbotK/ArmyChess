package common;
import java.io.Serializable;

public class NetMsg implements Serializable {
    public enum Type {
        LOGIN_SUCCESS,
        START_LAYOUT,
        SUBMIT_LAYOUT,
        LAYOUT_REJECT,
        GAME_START,
        MOVE_REQ,
        SURRENDER,
        UPDATE_BOARD,
        TURN_NOTIFY,
        MSG_TEXT,// 系统通知 (进入聊天框)
        GAME_OVER,
        CHAT      // 玩家发言 (进入聊天框) <--- 新增
    }

    public Type type;
    public Object data;
    public int playerId;

    public NetMsg(Type type, Object data, int playerId) {
        this.type = type;
        this.data = data;
        this.playerId = playerId;
    }
}