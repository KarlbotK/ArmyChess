import { describe, expect, it } from "vitest";
import {
  isCamp,
  isHeadquarters,
  isTeammate,
  legalDestinations,
  legalMoveOptions,
  STATIONS,
  territoryOwner,
  type BoardPiece,
} from "./board";
import type { PlayerId } from "./types";

describe("classic four-player board", () => {
  it("keeps 25 deployable slots for every player", () => {
    for (const player of [0, 1, 2, 3] as PlayerId[]) {
      const count = STATIONS.filter(({ x, y }) => territoryOwner(x, y) === player && !isCamp(x, y)).length;
      expect(count).toBe(25);
    }
  });

  it("preserves camp, headquarters and team relationships", () => {
    expect(isCamp(8, 13)).toBe(true);
    expect(isHeadquarters(7, 16)).toBe(true);
    expect(isTeammate(0, 2)).toBe(true);
    expect(isTeammate(0, 1)).toBe(false);
  });

  it("allows a normal front-line move into the center", () => {
    const piece: BoardPiece = { id: "p", owner: 0, type: "CAPTAIN", position: { x: 8, y: 11 } };
    expect(legalDestinations(piece, [piece])).toContainEqual({ x: 8, y: 10 });
  });

  it("keeps flags and landmines immobile", () => {
    const flag: BoardPiece = { id: "flag", owner: 0, type: "FLAG", position: { x: 7, y: 16 } };
    const mine: BoardPiece = { id: "mine", owner: 0, type: "LANDMINE", position: { x: 8, y: 15 } };
    expect(legalDestinations(flag, [flag])).toEqual([]);
    expect(legalDestinations(mine, [mine])).toEqual([]);
  });

  it("lets engineers turn through connected rail while ordinary pieces cannot", () => {
    const sapper: BoardPiece = { id: "s", owner: 0, type: "SAPPER", position: { x: 6, y: 5 } };
    const captain: BoardPiece = { ...sapper, id: "c", type: "CAPTAIN" };
    expect(legalDestinations(sapper, [sapper])).toContainEqual({ x: 10, y: 11 });
    expect(legalDestinations(captain, [captain])).not.toContainEqual({ x: 10, y: 11 });
  });

  it("lets an engineer take an alternate railway route around one blocker", () => {
    const sapper: BoardPiece = { id: "sapper", owner: 0, type: "SAPPER", position: { x: 10, y: 12 } };
    const blocker: BoardPiece = { id: "blocker", owner: 0, type: "MARSHAL", position: { x: 10, y: 11 } };

    expect(legalDestinations(sapper, [sapper, blocker])).toContainEqual({ x: 12, y: 10 });
  });

  it("returns the actual unblocked route used by an engineer", () => {
    const sapper: BoardPiece = { id: "sapper", owner: 0, type: "SAPPER", position: { x: 10, y: 12 } };
    const blocker: BoardPiece = { id: "blocker", owner: 0, type: "MARSHAL", position: { x: 10, y: 11 } };
    const option = legalMoveOptions(sapper, [sapper, blocker])
      .find(({ destination }) => destination.x === 12 && destination.y === 10);

    expect(option?.route[0]).toEqual(sapper.position);
    expect(option?.route.at(-1)).toEqual({ x: 12, y: 10 });
    expect(option?.route).not.toContainEqual(blocker.position);
    expect(option?.route.length).toBeGreaterThan(10);
  });

  it("does not let an engineer pass when every railway exit is blocked", () => {
    const sapper: BoardPiece = { id: "sapper", owner: 0, type: "SAPPER", position: { x: 10, y: 12 } };
    const blockers: BoardPiece[] = [
      { id: "north", owner: 0, type: "MARSHAL", position: { x: 10, y: 11 } },
      { id: "south", owner: 0, type: "GENERAL", position: { x: 10, y: 13 } },
    ];

    expect(legalDestinations(sapper, [sapper, ...blockers])).not.toContainEqual({ x: 12, y: 10 });
  });

  it("lets an engineer capture a railway blocker but never pass through it", () => {
    const sapper: BoardPiece = { id: "sapper", owner: 0, type: "SAPPER", position: { x: 10, y: 12 } };
    const enemy: BoardPiece = { id: "enemy", owner: 1, type: "CAPTAIN", position: { x: 10, y: 11 } };
    const rearBlocker: BoardPiece = { id: "rear", owner: 0, type: "GENERAL", position: { x: 10, y: 13 } };
    const destinations = legalDestinations(sapper, [sapper, enemy, rearBlocker]);

    expect(destinations).toContainEqual({ x: 10, y: 11 });
    expect(destinations).not.toContainEqual({ x: 10, y: 10 });
  });

  it.each([
    { x: 10, y: 11 },
    { x: 11, y: 10 },
  ])("does not let a railway piece bypass occupied curved junction $x,$y", (position) => {
    const bomb: BoardPiece = { id: "bomb", owner: 0, type: "BOMB", position: { x: 10, y: 12 } };
    const marshal: BoardPiece = { id: "marshal", owner: 0, type: "MARSHAL", position };

    expect(legalDestinations(bomb, [bomb, marshal])).not.toContainEqual({ x: 12, y: 10 });
  });

  it("returns every traversed junction for an ordinary curved railway move", () => {
    const captain: BoardPiece = { id: "captain", owner: 0, type: "CAPTAIN", position: { x: 10, y: 12 } };
    const option = legalMoveOptions(captain, [captain])
      .find(({ destination }) => destination.x === 12 && destination.y === 10);

    expect(option?.route).toEqual([
      { x: 10, y: 12 },
      { x: 10, y: 11 },
      { x: 11, y: 10 },
      { x: 12, y: 10 },
    ]);
  });
});
