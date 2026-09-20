import { describe, expect, it } from "vitest";
import { createDefaultLayout, fromViewer, isLayoutValid, rotateForViewer, validateLayout } from "./layout";

describe("layout and viewer rotation", () => {
  it("creates 25 valid pieces for each player", () => {
    for (const player of [0, 1, 2, 3] as const) {
      const layout = createDefaultLayout(player);
      expect(layout).toHaveLength(25);
      expect(new Set(layout.map(({ position }) => `${position.x},${position.y}`)).size).toBe(25);
      expect(isLayoutValid(validateLayout(player, layout))).toBe(true);
    }
  });

  it("flags illegal bomb and flag swaps before submission", () => {
    const layout = createDefaultLayout(0).map((piece) => ({ ...piece, position: { ...piece.position } }));
    const flag = layout.find(({ type }) => type === "FLAG")!;
    const front = layout.find(({ position }) => position.y === 11)!;
    [flag.position, front.position] = [front.position, flag.position];

    expect(validateLayout(0, layout).flagInHeadquarters).toBe(false);
  });

  it("places every viewer's own territory at the bottom", () => {
    for (const viewer of [0, 1, 2, 3] as const) {
      const own = createDefaultLayout(viewer)[0].position;
      const display = rotateForViewer(own, viewer);
      expect(display.y).toBeGreaterThanOrEqual(11);
      expect(fromViewer(display, viewer)).toEqual(own);
    }
  });
});
