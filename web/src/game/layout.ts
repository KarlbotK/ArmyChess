import {
  isCamp,
  isFirstRow,
  isHeadquarters,
  isLastTwoRows,
  territoryOwner,
} from "./board";
import type { Coordinate, PieceType, PlayerId } from "./types";

const PIECE_POOL: PieceType[] = [
  "FLAG",
  "LANDMINE", "LANDMINE", "LANDMINE",
  "BOMB", "BOMB",
  "MARSHAL", "GENERAL",
  "M_GENERAL", "M_GENERAL",
  "BRIGADIER", "BRIGADIER",
  "COLONEL", "COLONEL",
  "MAJOR", "MAJOR",
  "CAPTAIN", "CAPTAIN", "CAPTAIN",
  "LIEUTENANT", "LIEUTENANT", "LIEUTENANT",
  "SAPPER", "SAPPER", "SAPPER",
];

export interface LayoutPlacement {
  type: PieceType;
  position: Coordinate;
}

function slotsFor(player: PlayerId) {
  const slots: Coordinate[] = [];
  for (let y = 0; y < 17; y += 1) {
    for (let x = 0; x < 17; x += 1) {
      if (territoryOwner(x, y) === player && !isCamp(x, y)) slots.push({ x, y });
    }
  }
  return slots;
}

function shuffled<T>(items: readonly T[]) {
  const copy = [...items];
  for (let index = copy.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1));
    [copy[index], copy[swapIndex]] = [copy[swapIndex], copy[index]];
  }
  return copy;
}

export function createDefaultLayout(player: PlayerId, random = false): LayoutPlacement[] {
  const open = random ? shuffled(slotsFor(player)) : slotsFor(player);
  const layout: LayoutPlacement[] = [];
  const place = (type: PieceType, predicate: (slot: Coordinate) => boolean) => {
    const index = open.findIndex(predicate);
    if (index < 0) throw new Error(`NO_LAYOUT_SLOT:${type}`);
    const position = open.splice(index, 1)[0];
    layout.push({ type, position });
  };

  place("FLAG", ({ x, y }) => isHeadquarters(x, y));
  PIECE_POOL.filter((type) => type === "LANDMINE")
    .forEach((type) => place(type, ({ x, y }) => isLastTwoRows(player, x, y)));
  PIECE_POOL.filter((type) => type === "BOMB")
    .forEach((type) => place(type, ({ x, y }) => !isFirstRow(player, x, y)));
  PIECE_POOL.filter((type) => !["FLAG", "LANDMINE", "BOMB"].includes(type))
    .forEach((type) => place(type, () => true));
  return layout;
}

export interface LayoutRuleStatus {
  pieceCount: boolean;
  flagInHeadquarters: boolean;
  bombsBehindFront: boolean;
  minesInBackRows: boolean;
  campsEmpty: boolean;
  uniquePositions: boolean;
  allInTerritory: boolean;
}

export function validateLayout(player: PlayerId, layout: readonly LayoutPlacement[]): LayoutRuleStatus {
  const positions = layout.map(({ position }) => `${position.x},${position.y}`);
  return {
    pieceCount: layout.length === 25,
    flagInHeadquarters: layout.filter(({ type }) => type === "FLAG")
      .every(({ position }) => isHeadquarters(position.x, position.y)),
    bombsBehindFront: layout.filter(({ type }) => type === "BOMB")
      .every(({ position }) => !isFirstRow(player, position.x, position.y)),
    minesInBackRows: layout.filter(({ type }) => type === "LANDMINE")
      .every(({ position }) => isLastTwoRows(player, position.x, position.y)),
    campsEmpty: layout.every(({ position }) => !isCamp(position.x, position.y)),
    uniquePositions: new Set(positions).size === layout.length,
    allInTerritory: layout.every(({ position }) => territoryOwner(position.x, position.y) === player),
  };
}

export function isLayoutValid(status: LayoutRuleStatus) {
  return Object.values(status).every(Boolean);
}

export function rotateForViewer(position: Coordinate, viewer: PlayerId): Coordinate {
  if (viewer === 0) return { ...position };
  if (viewer === 1) return { x: position.y, y: 16 - position.x };
  if (viewer === 2) return { x: 16 - position.x, y: 16 - position.y };
  return { x: 16 - position.y, y: position.x };
}

export function fromViewer(position: Coordinate, viewer: PlayerId): Coordinate {
  if (viewer === 0) return { ...position };
  if (viewer === 1) return { x: 16 - position.y, y: position.x };
  if (viewer === 2) return { x: 16 - position.x, y: 16 - position.y };
  return { x: position.y, y: 16 - position.x };
}
