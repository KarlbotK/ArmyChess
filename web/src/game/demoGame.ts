import { keyOf, legalDestinations, legalMoveOptions, type BoardPiece, type LegalMoveOption } from "./board";
import { createDefaultLayout } from "./layout";
import type { Coordinate, GameSnapshot, PieceType, PlayerId } from "./types";

export type PublicGameEvent =
  | { kind: "MOVE_CONFIRMED"; actor: PlayerId; at: string }
  | { kind: "CLASH_OCCURRED"; actor: PlayerId; position: Coordinate; at: string };

export type DemoScenario = "standard" | "sapper-route" | "flag-capture" | "no-legal-move";

const RANK: Record<PieceType, number> = {
  MARSHAL: 40,
  GENERAL: 39,
  M_GENERAL: 38,
  BRIGADIER: 37,
  COLONEL: 36,
  MAJOR: 35,
  CAPTAIN: 34,
  LIEUTENANT: 33,
  SAPPER: 32,
  BOMB: 99,
  LANDMINE: 88,
  FLAG: 0,
};

function createArmy(player: PlayerId) {
  return createDefaultLayout(player).map<BoardPiece>((placement) => ({
    id: `piece-${crypto.randomUUID()}`,
    owner: player,
    type: placement.type,
    position: placement.position,
  }));
}

function createScenario(scenario: DemoScenario) {
  if (scenario !== "sapper-route") return ([0, 1, 2, 3] as PlayerId[]).flatMap(createArmy);
  return [
    { id: "scenario-sapper", owner: 0, type: "SAPPER", position: { x: 10, y: 12 } },
    { id: "scenario-blocker", owner: 0, type: "MARSHAL", position: { x: 10, y: 11 } },
    { id: "scenario-target", owner: 1, type: "CAPTAIN", position: { x: 12, y: 10 } },
  ] satisfies BoardPiece[];
}

function judge(attacker: PieceType, defender: PieceType) {
  if (defender === "FLAG") return 1;
  if (attacker === "BOMB" || defender === "BOMB") return 0;
  if (defender === "LANDMINE") return attacker === "SAPPER" ? 1 : -1;
  return Math.sign(RANK[attacker] - RANK[defender]);
}

export class DemoGame {
  private pieces: BoardPiece[];
  private revision = 1;

  constructor(scenario: DemoScenario = "standard") {
    this.pieces = createScenario(scenario);
  }

  snapshotFor(viewer: PlayerId): GameSnapshot {
    return {
      phase: "PLAYING",
      viewer,
      currentTurn: 0,
      revision: this.revision,
      turnDeadlineEpochMs: Date.now() + 30_000,
      alive: [true, true, true, true],
      timeoutCounts: [0, 0, 0, 0],
      winnerTeam: null,
      rematchVotes: 0,
      pieces: this.pieces.map((piece) => ({
        id: piece.id,
        owner: piece.owner,
        position: { ...piece.position },
        visibleType: piece.owner === viewer ? piece.type : null,
        revealed: piece.owner === viewer,
      })),
    };
  }

  firstPlayablePiece(viewer: PlayerId) {
    return this.pieces.find((piece) => piece.owner === viewer && legalDestinations(piece, this.pieces).length > 0)?.id ?? null;
  }

  legalMoves(pieceId: string) {
    const piece = this.pieces.find(({ id }) => id === pieceId);
    if (!piece || piece.owner !== 0) return [];
    return legalDestinations(piece, this.pieces);
  }

  moveOptions(pieceId: string): LegalMoveOption[] {
    const piece = this.pieces.find(({ id }) => id === pieceId);
    if (!piece || piece.owner !== 0) return [];
    return legalMoveOptions(piece, this.pieces);
  }

  move(pieceId: string, to: Coordinate): { snapshot: GameSnapshot; event: PublicGameEvent } {
    const piece = this.pieces.find(({ id }) => id === pieceId);
    if (!piece || !this.legalMoves(pieceId).some((candidate) => keyOf(candidate) === keyOf(to))) {
      throw new Error("ILLEGAL_MOVE");
    }
    const target = this.pieces.find(({ position }) => keyOf(position) === keyOf(to));
    let event: PublicGameEvent;
    if (!target) {
      piece.position = { ...to };
      event = { kind: "MOVE_CONFIRMED", actor: piece.owner, at: new Date().toISOString() };
    } else {
      const result = judge(piece.type, target.type);
      if (result >= 0) this.pieces = this.pieces.filter(({ id }) => id !== target.id);
      if (result <= 0) this.pieces = this.pieces.filter(({ id }) => id !== piece.id);
      if (result > 0) piece.position = { ...to };
      event = { kind: "CLASH_OCCURRED", actor: piece.owner, position: { ...to }, at: new Date().toISOString() };
    }
    this.revision += 1;
    return { snapshot: this.snapshotFor(0), event };
  }
}
