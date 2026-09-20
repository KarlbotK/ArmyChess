import { describe, expect, it } from "vitest";
import { publicEventText } from "./publicEvents";

describe("public event copy", () => {
  it("announces a captured flag without revealing the attacking piece", () => {
    expect(publicEventText({
      type: "PLAYER_ELIMINATED",
      actor: 1,
      reason: "FLAG_LOST",
      at: "2026-09-20T00:00:00.000Z",
    })).toBe("西家军旗被擒获，全军覆没");
  });

  it("announces defeat when a player has no legal move", () => {
    expect(publicEventText({
      type: "PLAYER_ELIMINATED",
      actor: 2,
      reason: "NO_LEGAL_MOVE",
      at: "2026-09-20T00:00:00.000Z",
    })).toBe("北家无棋可走，全军覆没");
  });
});
