import { describe, expect, it } from "vitest";
import {
  createDefaultLayout,
  fromViewer,
  isLayoutValid,
  LayoutRuleViolation,
  rotateForViewer,
  swapLayoutPieces,
  validateLayout,
} from "./layout";

describe("layout and viewer rotation", () => {
  it("creates 25 valid pieces for each player", () => {
    for (const player of [0, 1, 2, 3] as const) {
      const layout = createDefaultLayout(player);
      expect(layout).toHaveLength(25);
      expect(new Set(layout.map(({ position }) => `${position.x},${position.y}`)).size).toBe(25);
      expect(isLayoutValid(validateLayout(player, layout))).toBe(true);
    }
  });

  it.each([
    ["LANDMINE", "LANDMINE_REQUIRES_BACK_ROWS", "地雷只能放在最后两排"],
    ["BOMB", "BOMB_FORBIDDEN_ON_FRONT", "炸弹不能放在第一排"],
    ["FLAG", "FLAG_REQUIRES_HQ", "军旗只能放在大本营"],
  ] as const)("rejects an illegal %s swap before mutating the layout", (type, code, message) => {
    const layout = createDefaultLayout(0);
    const original = structuredClone(layout);
    const restrictedIndex = layout.findIndex((piece) => piece.type === type);
    const frontIndex = layout.findIndex(({ position }) => position.y === 11);

    expect(() => swapLayoutPieces(0, layout, restrictedIndex, frontIndex))
      .toThrowError(new LayoutRuleViolation(code, message));
    expect(layout).toEqual(original);
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
