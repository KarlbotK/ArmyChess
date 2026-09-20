import { useEffect, useMemo, useRef, useState } from "react";
import {
  isCamp,
  isCornerConnected,
  isHeadquarters,
  isNeighbor,
  isRail,
  isStation,
  isValidPoint,
  keyOf,
} from "../game/board";
import { PIECE_LABELS, PLAYER_META, type Coordinate, type PieceView } from "../game/types";

interface GameBoardProps {
  pieces: PieceView[];
  selectedId: string | null;
  legalMoves: Coordinate[];
  completedRoute?: readonly Coordinate[];
  onSelect: (pieceId: string) => void;
  onMove: (to: Coordinate) => void;
}

const CORNERS: [Coordinate, Coordinate, Coordinate][] = [
  [{ x: 5, y: 6 }, { x: 6, y: 5 }, { x: 5, y: 5 }],
  [{ x: 10, y: 5 }, { x: 11, y: 6 }, { x: 11, y: 5 }],
  [{ x: 11, y: 10 }, { x: 10, y: 11 }, { x: 11, y: 11 }],
  [{ x: 6, y: 11 }, { x: 5, y: 10 }, { x: 5, y: 11 }],
];

function drawBoard(canvas: HTMLCanvasElement, size: number) {
  const dpr = window.devicePixelRatio || 1;
  canvas.width = Math.round(size * dpr);
  canvas.height = Math.round(size * dpr);
  canvas.style.width = `${size}px`;
  canvas.style.height = `${size}px`;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, size, size);

  const margin = size * 0.05;
  const step = (size - margin * 2) / 16;
  const point = ({ x, y }: Coordinate) => ({ x: margin + x * step, y: margin + y * step });

  const drawSegment = (a: Coordinate, b: Coordinate) => {
    const p1 = point(a);
    const p2 = point(b);
    ctx.beginPath();
    ctx.moveTo(p1.x, p1.y);
    ctx.lineTo(p2.x, p2.y);
    ctx.stroke();
  };

  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  ctx.strokeStyle = "rgba(218, 224, 181, 0.54)";
  ctx.lineWidth = Math.max(1.2, size * 0.0018);
  for (let y = 0; y < 17; y += 1) {
    for (let x = 0; x < 17; x += 1) {
      if (!isValidPoint(x, y)) continue;
      const current = { x, y };
      const right = { x: x + 1, y };
      const down = { x, y: y + 1 };
      if (isNeighbor(current, right)) drawSegment(current, right);
      if (isNeighbor(current, down)) drawSegment(current, down);
      if (isCamp(x, y)) {
        for (const dx of [-1, 1]) {
          for (const dy of [-1, 1]) {
            const diagonal = { x: x + dx, y: y + dy };
            if (isValidPoint(diagonal.x, diagonal.y)) drawSegment(current, diagonal);
          }
        }
      }
    }
  }

  const railSegments: [Coordinate, Coordinate][] = [];
  for (let y = 0; y < 17; y += 1) {
    for (let x = 0; x < 17; x += 1) {
      if (!isRail(x, y)) continue;
      const current = { x, y };
      const right = { x: x + 1, y };
      const down = { x, y: y + 1 };
      if (isRail(right.x, right.y) && isNeighbor(current, right)) railSegments.push([current, right]);
      if (isRail(down.x, down.y) && isNeighbor(current, down)) railSegments.push([current, down]);
    }
  }

  for (const [color, width] of [["rgba(37, 67, 57, 0.58)", size * 0.010], ["#dce4bf", size * 0.006]] as const) {
    ctx.strokeStyle = color;
    ctx.lineWidth = Math.max(2, width);
    railSegments.forEach(([a, b]) => drawSegment(a, b));
    CORNERS.forEach(([a, b, control]) => {
      const p1 = point(a);
      const p2 = point(b);
      const pc = point(control);
      ctx.beginPath();
      ctx.moveTo(p1.x, p1.y);
      ctx.quadraticCurveTo(pc.x, pc.y, p2.x, p2.y);
      ctx.stroke();
    });
  }

  for (let y = 0; y < 17; y += 1) {
    for (let x = 0; x < 17; x += 1) {
      if (!isStation(x, y)) continue;
      const p = point({ x, y });
      if (isCamp(x, y)) {
        ctx.beginPath();
        ctx.fillStyle = "#315b42";
        ctx.strokeStyle = "#d7bd7e";
        ctx.lineWidth = Math.max(1.5, size * 0.003);
        ctx.arc(p.x, p.y, step * 0.28, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
      } else if (isHeadquarters(x, y)) {
        const box = step * 0.48;
        ctx.strokeStyle = "#9c6237";
        ctx.lineWidth = Math.max(1.5, size * 0.004);
        ctx.strokeRect(p.x - box / 2, p.y - box / 2, box, box);
        ctx.beginPath();
        ctx.moveTo(p.x - box / 2, p.y - box / 2);
        ctx.lineTo(p.x + box / 2, p.y + box / 2);
        ctx.moveTo(p.x + box / 2, p.y - box / 2);
        ctx.lineTo(p.x - box / 2, p.y + box / 2);
        ctx.stroke();
      } else {
        ctx.beginPath();
        ctx.fillStyle = isRail(x, y) ? "#dce4bf" : "#76906b";
        ctx.arc(p.x, p.y, Math.max(1.8, size * 0.003), 0, Math.PI * 2);
        ctx.fill();
      }
    }
  }
}

const positionStyle = ({ x, y }: Coordinate) => ({
  left: `${5 + x * 5.625}%`,
  top: `${5 + y * 5.625}%`,
});

const routePoint = ({ x, y }: Coordinate) => `${5 + x * 5.625},${5 + y * 5.625}`;

export function GameBoard({ pieces, selectedId, legalMoves, completedRoute = [], onSelect, onMove }: GameBoardProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [size, setSize] = useState(760);
  const legalKeys = useMemo(() => new Set(legalMoves.map(keyOf)), [legalMoves]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const resize = () => setSize(host.clientWidth);
    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(host);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (canvasRef.current) drawBoard(canvasRef.current, size);
  }, [size]);

  return (
    <div ref={hostRef} className="game-board" aria-label="四国军棋棋盘">
      <canvas ref={canvasRef} className="game-board__canvas" aria-hidden="true" />
      {completedRoute.length > 1 && (
        <svg className="game-board__routes" viewBox="0 0 100 100" aria-hidden="true">
          <polyline
            className="game-board__route-completed"
            pathLength="1"
            points={completedRoute.map(routePoint).join(" ")}
          />
          <circle
            className="game-board__route-end"
            cx={5 + completedRoute.at(-1)!.x * 5.625}
            cy={5 + completedRoute.at(-1)!.y * 5.625}
            r="1.35"
          />
        </svg>
      )}
      {completedRoute.length > 1 && (
        <span className="sr-only" role="status">最近一步经过 {completedRoute.length - 1} 段路径</span>
      )}
      <div className="game-board__pieces">
        {pieces.map((piece) => {
          const canMoveHere = selectedId !== null && legalKeys.has(keyOf(piece.position));
          const label = piece.visibleType ? PIECE_LABELS[piece.visibleType] : "";
          const owner = PLAYER_META[piece.owner];
          return (
            <button
              type="button"
              key={piece.id}
              className={`game-piece game-piece--owner-${piece.owner}${selectedId === piece.id ? " is-selected" : ""}${piece.visibleType ? " is-known" : " is-hidden"}${piece.visibleType === "FLAG" ? " is-flag" : ""}`}
              style={positionStyle(piece.position)}
              aria-label={piece.visibleType ? `${owner.direction}${label}` : `${owner.direction}暗棋`}
              aria-pressed={selectedId === piece.id}
              onClick={() => canMoveHere ? onMove(piece.position) : onSelect(piece.id)}
            >
              {label}
            </button>
          );
        })}
        {legalMoves.filter((move) => !pieces.some(({ position }) => keyOf(position) === keyOf(move))).map((move) => (
          <button
            type="button"
            key={keyOf(move)}
            className="legal-move"
            style={positionStyle(move)}
            onClick={() => onMove(move)}
            aria-label={`移动到第 ${move.x + 1} 列第 ${move.y + 1} 行`}
          >
            <span className="sr-only">合法落点</span>
          </button>
        ))}
      </div>
    </div>
  );
}
