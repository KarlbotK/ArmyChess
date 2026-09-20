import type { Coordinate, GameSnapshot, PlayerId } from "../game/types";

export const PROTOCOL_VERSION = 1 as const;

export type ClientMessage =
  | { v: 1; type: "SESSION_RESUME"; requestId: string; roomCode: string; resumeToken: string }
  | { v: 1; type: "SUBMIT_LAYOUT"; requestId: string; placements: Array<{ type: string; position: Coordinate }> }
  | { v: 1; type: "MOVE_REQUEST"; requestId: string; pieceId: string; to: Coordinate; expectedRevision: number }
  | { v: 1; type: "CHAT_SEND"; requestId: string; text: string }
  | { v: 1; type: "SURRENDER_REQUEST"; requestId: string }
  | { v: 1; type: "PING"; requestId: string; sentAt: number };

export type PublicEvent =
  | { type: "MOVE_CONFIRMED"; actor: PlayerId; at: string }
  | { type: "CLASH_OCCURRED"; actor: PlayerId; position: Coordinate; at: string }
  | { type: "PLAYER_ELIMINATED"; actor: PlayerId; reason: "FLAG_LOST" | "SURRENDER" | "TIMEOUT" | "NO_LEGAL_MOVE"; at: string };

export interface RoomPlayer {
  playerId: PlayerId;
  nickname: string;
}

export type ServerMessage =
  | { v: 1; type: "SESSION_READY"; roomCode: string; playerId: PlayerId; players: RoomPlayer[] }
  | { v: 1; type: "ROOM_STATE"; roomCode: string; phase: "LAYOUT" | "PLAYING" | "FINISHED"; players: RoomPlayer[] }
  | { v: 1; type: "SNAPSHOT"; requestId?: string; snapshot: GameSnapshot }
  | { v: 1; type: "PUBLIC_EVENT"; event: PublicEvent }
  | { v: 1; type: "ACTION_ACCEPTED"; requestId: string }
  | { v: 1; type: "ACTION_REJECTED"; requestId: string; code: string; message: string }
  | { v: 1; type: "CHAT_MESSAGE"; player: PlayerId; text: string; at: string }
  | { v: 1; type: "PONG"; requestId: string; sentAt: number; serverAt: number };

/**
 * Privacy invariant: public battle events intentionally contain no attacker type,
 * defender type, winner piece, loser piece, or equivalent identity mapping.
 * Hidden information can only arrive through the viewer-scoped snapshot.
 */
export function isServerMessage(value: unknown): value is ServerMessage {
  if (!value || typeof value !== "object") return false;
  const message = value as Record<string, unknown>;
  return message.v === PROTOCOL_VERSION && typeof message.type === "string";
}
