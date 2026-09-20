export type PlayerId = 0 | 1 | 2 | 3;

export type PieceType =
  | "MARSHAL"
  | "GENERAL"
  | "M_GENERAL"
  | "BRIGADIER"
  | "COLONEL"
  | "MAJOR"
  | "CAPTAIN"
  | "LIEUTENANT"
  | "SAPPER"
  | "BOMB"
  | "LANDMINE"
  | "FLAG";

export interface Coordinate {
  x: number;
  y: number;
}

export interface PieceView {
  id: string;
  owner: PlayerId;
  position: Coordinate;
  visibleType: PieceType | null;
  revealed: boolean;
}

export interface GameSnapshot {
  phase: "LAYOUT" | "PLAYING" | "FINISHED";
  viewer: PlayerId;
  currentTurn: PlayerId;
  pieces: PieceView[];
  revision: number;
}

export const PIECE_LABELS: Record<PieceType, string> = {
  MARSHAL: "司令",
  GENERAL: "军长",
  M_GENERAL: "师长",
  BRIGADIER: "旅长",
  COLONEL: "团长",
  MAJOR: "营长",
  CAPTAIN: "连长",
  LIEUTENANT: "排长",
  SAPPER: "工兵",
  BOMB: "炸弹",
  LANDMINE: "地雷",
  FLAG: "军旗",
};

export const PLAYER_META: Record<
  PlayerId,
  { direction: string; name: string; team: string; tone: string }
> = {
  0: { direction: "南家", name: "松风", team: "南北队", tone: "amber" },
  1: { direction: "西家", name: "星河", team: "东西队", tone: "violet" },
  2: { direction: "北家", name: "清风", team: "南北队", tone: "blue" },
  3: { direction: "东家", name: "笑看风云", team: "东西队", tone: "coral" },
};
