import type { PublicEvent } from "../network/protocol";
import { PLAYER_META } from "./types";

export function publicEventText(event: PublicEvent) {
  const actor = PLAYER_META[event.actor].direction;
  if (event.type === "CLASH_OCCURRED") return "棋盘上发生交锋";
  if (event.type === "MOVE_CONFIRMED") return `${actor}完成移动`;
  if (event.type === "TURN_TIMED_OUT") return `${actor}超时，自动跳过（${event.timeoutCount}/5）`;
  if (event.reason === "FLAG_LOST") return `${actor}军旗被擒获，全军覆没`;
  if (event.reason === "NO_LEGAL_MOVE") return `${actor}无棋可走，全军覆没`;
  if (event.reason === "SURRENDER") return `${actor}投降，全军覆没`;
  return `${actor}连续超时，全军覆没`;
}
