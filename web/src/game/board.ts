import type { Coordinate, PieceType, PlayerId } from "./types";

export const BOARD_SIZE = 17;

export const keyOf = ({ x, y }: Coordinate) => `${x},${y}`;

const CAMPS = new Set([
  "7,2", "9,2", "8,3", "7,4", "9,4",
  "7,12", "9,12", "8,13", "7,14", "9,14",
  "2,7", "2,9", "3,8", "4,7", "4,9",
  "12,7", "12,9", "13,8", "14,7", "14,9",
]);

const HQS = new Set(["7,0", "9,0", "7,16", "9,16", "0,7", "0,9", "16,7", "16,9"]);

const RAILS = new Set<string>();

for (let y = 6; y <= 10; y += 1) {
  RAILS.add(`6,${y}`);
  RAILS.add(`8,${y}`);
  RAILS.add(`10,${y}`);
}
for (let x = 6; x <= 10; x += 1) {
  RAILS.add(`${x},6`);
  RAILS.add(`${x},8`);
  RAILS.add(`${x},10`);
}
for (let x = 6; x <= 10; x += 1) {
  RAILS.add(`${x},5`);
  RAILS.add(`${x},1`);
  RAILS.add(`${x},11`);
  RAILS.add(`${x},15`);
}
for (let y = 1; y <= 5; y += 1) {
  RAILS.add(`6,${y}`);
  RAILS.add(`10,${y}`);
}
for (let y = 11; y <= 15; y += 1) {
  RAILS.add(`6,${y}`);
  RAILS.add(`10,${y}`);
}
for (let y = 6; y <= 10; y += 1) {
  RAILS.add(`5,${y}`);
  RAILS.add(`1,${y}`);
  RAILS.add(`11,${y}`);
  RAILS.add(`15,${y}`);
}
for (let x = 1; x <= 5; x += 1) {
  RAILS.add(`${x},6`);
  RAILS.add(`${x},10`);
}
for (let x = 11; x <= 15; x += 1) {
  RAILS.add(`${x},6`);
  RAILS.add(`${x},10`);
}

export const isCamp = (x: number, y: number) => CAMPS.has(`${x},${y}`);
export const isHeadquarters = (x: number, y: number) => HQS.has(`${x},${y}`);
export const isRail = (x: number, y: number) => RAILS.has(`${x},${y}`);

export function isValidPoint(x: number, y: number) {
  if (x >= 6 && x <= 10 && ((y >= 0 && y <= 5) || (y >= 11 && y <= 16))) return true;
  if (y >= 6 && y <= 10 && ((x >= 0 && x <= 5) || (x >= 11 && x <= 16))) return true;
  if (x >= 6 && x <= 10 && y >= 6 && y <= 10) return isRail(x, y);
  return false;
}

export function isStation(x: number, y: number) {
  if (!isValidPoint(x, y)) return false;
  if (x >= 6 && x <= 10 && y >= 6 && y <= 10) return x % 2 === 0 && y % 2 === 0;
  return true;
}

function isBlockedPath(x1: number, y1: number, x2: number, y2: number) {
  if (y1 === y2) {
    const minX = Math.min(x1, x2);
    if ((minX === 5 || minX === 10) && (y1 === 7 || y1 === 9)) return true;
  }
  if (x1 === x2) {
    const minY = Math.min(y1, y2);
    if ((minY === 5 || minY === 10) && (x1 === 7 || x1 === 9)) return true;
  }
  return false;
}

export function isCornerConnected(a: Coordinate, b: Coordinate) {
  const pairs: [Coordinate, Coordinate][] = [
    [{ x: 5, y: 6 }, { x: 6, y: 5 }],
    [{ x: 10, y: 5 }, { x: 11, y: 6 }],
    [{ x: 11, y: 10 }, { x: 10, y: 11 }],
    [{ x: 6, y: 11 }, { x: 5, y: 10 }],
  ];
  return pairs.some(([p1, p2]) =>
    (p1.x === a.x && p1.y === a.y && p2.x === b.x && p2.y === b.y) ||
    (p1.x === b.x && p1.y === b.y && p2.x === a.x && p2.y === a.y),
  );
}

export function isNeighbor(a: Coordinate, b: Coordinate) {
  if (!isValidPoint(a.x, a.y) || !isValidPoint(b.x, b.y)) return false;
  const dx = Math.abs(a.x - b.x);
  const dy = Math.abs(a.y - b.y);
  if (dx === 0 && dy === 0) return false;
  if ((isCamp(a.x, a.y) || isCamp(b.x, b.y)) && dx <= 1 && dy <= 1) return true;
  if (dx + dy === 1) return !isBlockedPath(a.x, a.y, b.x, b.y);
  return isCornerConnected(a, b);
}

export function territoryOwner(x: number, y: number): PlayerId | null {
  if (y >= 11 && y <= 16 && x >= 6 && x <= 10) return 0;
  if (x >= 0 && x <= 5 && y >= 6 && y <= 10) return 1;
  if (y >= 0 && y <= 5 && x >= 6 && x <= 10) return 2;
  if (x >= 11 && x <= 16 && y >= 6 && y <= 10) return 3;
  return null;
}

export const isTeammate = (a: PlayerId, b: PlayerId) => a % 2 === b % 2;

export function isLastTwoRows(player: PlayerId, x: number, y: number) {
  if (player === 0) return y >= 15;
  if (player === 2) return y <= 1;
  if (player === 1) return x <= 1;
  return x >= 15;
}

export function isFirstRow(player: PlayerId, x: number, y: number) {
  if (player === 0) return y === 11;
  if (player === 2) return y === 5;
  if (player === 1) return x === 5;
  return x === 11;
}

export const STATIONS: Coordinate[] = Array.from({ length: BOARD_SIZE * BOARD_SIZE }, (_, index) => ({
  x: index % BOARD_SIZE,
  y: Math.floor(index / BOARD_SIZE),
})).filter(({ x, y }) => isStation(x, y));

const VALID_POINTS: Coordinate[] = Array.from({ length: BOARD_SIZE * BOARD_SIZE }, (_, index) => ({
  x: index % BOARD_SIZE,
  y: Math.floor(index / BOARD_SIZE),
})).filter(({ x, y }) => isValidPoint(x, y));

export interface BoardPiece {
  id: string;
  owner: PlayerId;
  type: PieceType;
  position: Coordinate;
}

function pathClear(from: Coordinate, to: Coordinate, occupied: ReadonlySet<string>) {
  if (isCornerConnected(from, to)) return true;
  const dx = Math.sign(to.x - from.x);
  const dy = Math.sign(to.y - from.y);
  let x = from.x + dx;
  let y = from.y + dy;
  while (x !== to.x || y !== to.y) {
    if (!isRail(x, y) || occupied.has(`${x},${y}`)) return false;
    x += dx;
    y += dy;
  }
  return true;
}

function onNorthRight(p: Coordinate) { return p.x === 10 && p.y <= 5; }
function onEastTop(p: Coordinate) { return p.y === 6 && p.x >= 11; }
function onEastBottom(p: Coordinate) { return p.y === 10 && p.x >= 11; }
function onSouthRight(p: Coordinate) { return p.x === 10 && p.y >= 11; }
function onSouthLeft(p: Coordinate) { return p.x === 6 && p.y >= 11; }
function onWestBottom(p: Coordinate) { return p.y === 10 && p.x <= 5; }
function onWestTop(p: Coordinate) { return p.y === 6 && p.x <= 5; }
function onNorthLeft(p: Coordinate) { return p.x === 6 && p.y <= 5; }

function curveClear(from: Coordinate, to: Coordinate, occupied: ReadonlySet<string>) {
  const routes: [
    (p: Coordinate) => boolean,
    (p: Coordinate) => boolean,
    Coordinate,
    Coordinate,
  ][] = [
    [onNorthRight, onEastTop, { x: 10, y: 5 }, { x: 11, y: 6 }],
    [onEastTop, onNorthRight, { x: 11, y: 6 }, { x: 10, y: 5 }],
    [onEastBottom, onSouthRight, { x: 11, y: 10 }, { x: 10, y: 11 }],
    [onSouthRight, onEastBottom, { x: 10, y: 11 }, { x: 11, y: 10 }],
    [onSouthLeft, onWestBottom, { x: 6, y: 11 }, { x: 5, y: 10 }],
    [onWestBottom, onSouthLeft, { x: 5, y: 10 }, { x: 6, y: 11 }],
    [onWestTop, onNorthLeft, { x: 5, y: 6 }, { x: 6, y: 5 }],
    [onNorthLeft, onWestTop, { x: 6, y: 5 }, { x: 5, y: 6 }],
  ];
  return routes.some(([startOn, endOn, cornerA, cornerB]) =>
    startOn(from) && endOn(to) && pathClear(from, cornerA, occupied) && pathClear(cornerB, to, occupied),
  );
}

function sapperCanReach(from: Coordinate, to: Coordinate, occupied: ReadonlySet<string>) {
  const queue: Coordinate[] = [from];
  const visited = new Set([keyOf(from)]);
  while (queue.length > 0) {
    const current = queue.shift()!;
    if (current.x === to.x && current.y === to.y) return true;
    for (const candidate of VALID_POINTS) {
      if (!isRail(candidate.x, candidate.y) || !isRail(current.x, current.y)) continue;
      if (!isNeighbor(current, candidate)) continue;
      const key = keyOf(candidate);
      if (visited.has(key)) continue;
      if (occupied.has(key) && key !== keyOf(to)) continue;
      visited.add(key);
      queue.push(candidate);
    }
  }
  return false;
}

export function isValidMovePath(
  piece: BoardPiece,
  to: Coordinate,
  occupied: ReadonlySet<string>,
) {
  const from = piece.position;
  if (!isStation(to.x, to.y)) return false;
  if (piece.type === "LANDMINE" || piece.type === "FLAG" || isHeadquarters(from.x, from.y)) return false;
  if (isNeighbor(from, to)) return true;
  if (!isRail(from.x, from.y) || !isRail(to.x, to.y)) return false;
  if (piece.type === "SAPPER") return sapperCanReach(from, to, occupied);
  if (from.x === to.x || from.y === to.y) return pathClear(from, to, occupied);
  return curveClear(from, to, occupied);
}

export function legalDestinations(piece: BoardPiece, pieces: readonly BoardPiece[]) {
  const occupied = new Set(pieces.map(({ position }) => keyOf(position)));
  return STATIONS.filter((to) => {
    const target = pieces.find(({ position }) => position.x === to.x && position.y === to.y);
    if (target && (isTeammate(piece.owner, target.owner) || isCamp(to.x, to.y))) return false;
    return isValidMovePath(piece, to, occupied);
  });
}
