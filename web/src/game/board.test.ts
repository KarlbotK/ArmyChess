import { describe, expect, it } from "vitest";
import {
  isCamp,
  isHeadquarters,
  isTeammate,
  legalDestinations,
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
});
