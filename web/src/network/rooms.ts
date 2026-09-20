import type { PlayerId } from "../game/types";

export interface SessionTicket {
  roomCode: string;
  playerId: PlayerId;
  nickname: string;
  resumeToken: string;
}

async function request(path: string, nickname: string): Promise<SessionTicket> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ nickname }),
  });
  const body = await response.json().catch(() => null) as SessionTicket | { message?: string } | null;
  if (!response.ok) throw new Error(body && "message" in body && body.message ? body.message : "服务暂时不可用");
  return body as SessionTicket;
}

export const createRoom = (nickname: string) => request("/api/rooms", nickname);
export const joinRoom = (roomCode: string, nickname: string) => request(`/api/rooms/${encodeURIComponent(roomCode)}/join`, nickname);

export function socketUrl(ticket: SessionTicket) {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const configured = import.meta.env.VITE_WS_URL as string | undefined;
  const base = configured ?? `${protocol}//${window.location.host}/ws/game`;
  const url = new URL(base);
  url.searchParams.set("roomCode", ticket.roomCode);
  url.searchParams.set("token", ticket.resumeToken);
  return url.toString();
}
