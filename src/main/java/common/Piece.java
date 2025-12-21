package common;
import java.io.Serializable;

public class Piece implements Serializable {
    public enum Type {
        MARSHAL(40, "司令"), GENERAL(39, "军长"), M_GENERAL(38, "师长"),
        BRIGADIER(37, "旅长"), COLONEL(36, "团长"), MAJOR(35, "营长"),
        CAPTAIN(34, "连长"), LIEUTENANT(33, "排长"), SAPPER(32, "工兵"),
        BOMB(99, "炸弹"), LANDMINE(88, "地雷"), FLAG(0, "军旗"),
        UNKNOWN(-1, "暗");

        public final int rank;
        public final String name;
        Type(int rank, String name) { this.rank = rank; this.name = name; }
    }

    private Type type;
    private int ownerId;
    private boolean isRevealed = false; // 是否明牌

    public Piece(Type type, int ownerId) {
        this.type = type;
        this.ownerId = ownerId;
    }

    public Type getType() { return type; }
    public int getOwnerId() { return ownerId; }
    public boolean isRevealed() { return isRevealed; }
    public void setRevealed(boolean revealed) { isRevealed = revealed; }

    // 生成用于发送给敌人的副本（隐藏真实信息）
    public Piece getMaskedCopy() {
        if (isRevealed) return this;
        return new Piece(Type.UNKNOWN, this.ownerId);
    }
}