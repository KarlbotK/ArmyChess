import { describe, expect, it } from "vitest";
import { DemoGame } from "../game/demoGame";
import type { PublicEvent } from "./protocol";

describe("hidden-information protocol", () => {
  it("masks every opponent piece in a viewer snapshot", () => {
    const snapshot = new DemoGame().snapshotFor(0);
    expect(snapshot.pieces.filter((piece) => piece.owner !== 0).every((piece) => piece.visibleType === null)).toBe(true);
    expect(snapshot.pieces.filter((piece) => piece.owner === 0).every((piece) => piece.visibleType !== null)).toBe(true);
    const secretNames = ["marshal", "general", "bomb", "landmine", "flag", "司令", "炸弹", "军旗"];
    expect(snapshot.pieces.filter((piece) => piece.owner !== 0).every((piece) =>
      secretNames.every((name) => !piece.id.toLowerCase().includes(name)),
    )).toBe(true);
  });

  it("public clash events contain no defeated-piece identity", () => {
    const event: PublicEvent = {
      type: "CLASH_OCCURRED",
      actor: 0,
      position: { x: 8, y: 8 },
      at: "2026-09-20T00:00:00.000Z",
    };
    const keys = Object.keys(event);
    expect(keys).not.toContain("attackerType");
    expect(keys).not.toContain("defenderType");
    expect(keys).not.toContain("winnerPiece");
    expect(keys).not.toContain("loserPiece");
  });
});
