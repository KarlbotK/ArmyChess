import { Circle, Diamond, StarFour, Triangle } from "@phosphor-icons/react";
import { PLAYER_META, type PlayerId } from "../game/types";

const EMBLEMS = {
  0: StarFour,
  1: Diamond,
  2: Triangle,
  3: Circle,
} as const;

interface PlayerPlateProps {
  player: PlayerId;
  position: "north" | "south" | "west" | "east";
  active?: boolean;
  name?: string;
  self?: boolean;
  online?: boolean;
}

export function PlayerPlate({ player, position, active = false, name, self, online = true }: PlayerPlateProps) {
  const meta = PLAYER_META[player];
  const Emblem = EMBLEMS[player];
  return (
    <div className={`player-plate player-plate--${position} player-plate--${meta.tone}${active ? " is-active" : ""}`}>
      <span className="player-plate__emblem" aria-hidden="true">
        <Emblem weight="bold" />
      </span>
      <span className="player-plate__copy">
        <strong>{meta.direction}</strong>
        <span>{(self ?? player === 0) ? "我 · " : ""}{name ?? meta.name}</span>
      </span>
      <span className="player-plate__online">
        <span className={`status-dot${online ? "" : " is-offline"}`} aria-hidden="true" />
        <span className="sr-only">{online ? "在线" : "等待加入"}</span>
      </span>
    </div>
  );
}
