package common;

import java.io.Serializable;

public class Piece implements Serializable {

    // --- Part A: 枚举定义 (说明书) ---
    // 这里定义了所有可能的棋子类型，以及它们的战斗力和名字
    // 枚举就像是一个只读的表格，规定了只能用这些词，防止你手误写成 "Siling"
    public enum Type {
        // 参数1：军衔值(越大越厉害)， 参数2：显示的名字
        MARSHAL(40, "司令"),
        GENERAL(39, "军长"),
        M_GENERAL(38, "师长"),
        BRIGADIER(37, "旅长"),
        COLONEL(36, "团长"),
        MAJOR(35, "营长"),
        CAPTAIN(34, "连长"),
        LIEUTENANT(33, "排长"),
        SAPPER(32, "工兵"),

        // 特殊棋子
        BOMB(99, "炸弹"),      // 炸弹设个特殊值，逻辑里单独判断
        LANDMINE(88, "地雷"),  // 地雷设个特殊值
        FLAG(0, "军旗"),       // 军旗最小

        UNKNOWN(-1, "?");      // 暗棋专用，表示“未知”，背面向上

        public final int rank;   // 战斗力
        public final String name; // 名字

        // 枚举的构造函数
        Type(int rank, String name) {
            this.rank = rank;
            this.name = name;
        }
    }

    // --- Part B: 棋子属性 (实体) ---
    private Type type;      // 它是谁？(司令？工兵？)
    private int ownerId;    // 它是谁家的？(0,1,2,3)
    private boolean isRevealed; // 它亮牌了吗？(true=亮了, false=暗的)

    // 构造方法：造棋子的时候，必须指定身份和主人
    public Piece(Type type, int ownerId) {
        this.type = type;
        this.ownerId = ownerId;
        this.isRevealed = false; // 默认都是暗的
    }

    // --- Part C: 核心方法 ---

    // Getter 方法
    public Type getType() { return type; }
    public int getOwnerId() { return ownerId; }
    public boolean isRevealed() { return isRevealed; }
    public void setRevealed(boolean revealed) { isRevealed = revealed; }

    // 防作弊核心：给敌人发数据时，调用这个方法“穿马甲”
    public Piece getMaskedCopy() {
        if (this.isRevealed) {
            return this; // 如果已经亮牌，发真身
        }
        // 如果没亮牌，发一个假的“未知”棋子，但保留颜色(ownerId)，否则客户端没法画框框
        return new Piece(Type.UNKNOWN, this.ownerId);
    }
}
