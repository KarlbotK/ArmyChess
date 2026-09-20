import {
  BookOpen,
  Check,
  CopySimple,
  Flag,
  Mountains,
  WifiHigh,
} from "@phosphor-icons/react";
import { useEffect, useRef, useState } from "react";
import { ActivityPanel, type ActivityItem } from "./components/ActivityPanel";
import { GameBoard } from "./components/GameBoard";
import { Lobby } from "./components/Lobby";
import { OnlineGame } from "./components/OnlineGame";
import { ChatDrawer, RulesDialog, SurrenderDialog } from "./components/Overlays";
import { PlayerPlate } from "./components/PlayerPlate";
import { DemoGame } from "./game/demoGame";
import { keyOf } from "./game/board";
import { PLAYER_META, type Coordinate } from "./game/types";
import type { SessionTicket } from "./network/rooms";

const INITIAL_ACTIVITY: ActivityItem[] = [
  { id: "a1", text: "北家完成移动", time: "20:12", tone: "blue" },
  { id: "a2", text: "中央铁路发生交锋", time: "20:09", tone: "neutral" },
  { id: "a3", text: "东家进入行营", time: "20:06", tone: "coral" },
];

function currentTime() {
  return new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date());
}

function DemoGameScreen() {
  const gameRef = useRef<DemoGame | null>(null);
  if (!gameRef.current) gameRef.current = new DemoGame();
  const game = gameRef.current;

  const [snapshot, setSnapshot] = useState(() => game.snapshotFor(0));
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [legalMoves, setLegalMoves] = useState<Coordinate[]>([]);
  const [seconds, setSeconds] = useState(24);
  const [activity, setActivity] = useState(INITIAL_ACTIVITY);
  const [rulesOpen, setRulesOpen] = useState(false);
  const [surrenderOpen, setSurrenderOpen] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [toast, setToast] = useState("请选择一枚自己的棋子");

  useEffect(() => {
    const initial = game.firstPlayablePiece(0);
    if (initial) {
      setSelectedId(initial);
      setLegalMoves(game.legalMoves(initial));
      setToast("已为你标出可走位置");
    }
  }, [game]);

  useEffect(() => {
    const timer = window.setInterval(() => setSeconds((value) => value > 0 ? value - 1 : 30), 1_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 2_400);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const handleSelect = (pieceId: string) => {
    const piece = snapshot.pieces.find(({ id }) => id === pieceId);
    if (!piece || piece.owner !== snapshot.viewer) {
      setToast("暗棋身份不可查看");
      return;
    }
    if (selectedId === pieceId) {
      setSelectedId(null);
      setLegalMoves([]);
      return;
    }
    const nextMoves = game.legalMoves(pieceId);
    setSelectedId(pieceId);
    setLegalMoves(nextMoves);
    setToast(nextMoves.length > 0 ? `可走 ${nextMoves.length} 个位置` : "这枚棋子当前无法移动");
  };

  const handleMove = (to: Coordinate) => {
    if (!selectedId || !legalMoves.some((move) => keyOf(move) === keyOf(to))) return;
    try {
      const result = game.move(selectedId, to);
      setSnapshot(result.snapshot);
      setSelectedId(null);
      setLegalMoves([]);
      setSeconds(30);
      const isClash = result.event.kind === "CLASH_OCCURRED";
      setActivity((items) => [{
        id: `${result.event.at}-${result.event.kind}`,
        text: isClash ? "棋盘上发生交锋" : "南家完成移动",
        time: currentTime(),
        tone: isClash ? "neutral" : "coral",
      }, ...items]);
      setToast(isClash ? "交锋结果已同步到棋盘" : "落子成功");
    } catch {
      setToast("这个位置现在不能走");
    }
  };

  const copyRoomCode = async () => {
    try {
      await navigator.clipboard.writeText("482691");
    } catch {
      // Clipboard access can be blocked by the browser; visual confirmation still helps.
    }
    setCopied(true);
    setToast("房间号已复制");
    window.setTimeout(() => setCopied(false), 1_800);
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand" aria-label="四国军棋 Online">
          <Mountains weight="fill" aria-hidden="true" />
          <span>四国军棋</span>
          <em>Online</em>
        </div>
        <button className="room-code" type="button" onClick={copyRoomCode} aria-label="复制房间号 482691">
          <span>房间号：482691</span>
          {copied ? <Check weight="bold" /> : <CopySimple />}
        </button>
        <div className="turn-status" aria-live="polite">
          <span>你的回合</span><small>南 → 西 → 北 → 东</small>
          <strong>00:{String(seconds).padStart(2, "0")}</strong>
        </div>
        <div className="topbar__meta">
          <span className="network-state"><WifiHigh weight="bold" aria-hidden="true" /> 32 ms</span>
          <button className="topbar-action" type="button" onClick={() => setRulesOpen(true)}><BookOpen aria-hidden="true" />规则</button>
          <span className="topbar-divider" aria-hidden="true" />
          <button className="topbar-action" type="button" onClick={() => setSurrenderOpen(true)}><Flag aria-hidden="true" />投降</button>
        </div>
      </header>

      <main className="game-surface">
        <ActivityPanel items={activity} />
        <section className="board-stage" aria-label="当前对局">
          <PlayerPlate player={2} position="north" />
          <PlayerPlate player={1} position="west" />
          <PlayerPlate player={3} position="east" />
          <PlayerPlate player={0} position="south" active />
          <GameBoard
            pieces={snapshot.pieces}
            selectedId={selectedId}
            legalMoves={legalMoves}
            onSelect={handleSelect}
            onMove={handleMove}
          />
        </section>
        <div className="team-note" aria-label="我的队伍">
          <span className="team-note__mark" aria-hidden="true">✦</span>
          <span><strong>{PLAYER_META[0].team}</strong><small>与北家并肩作战</small></span>
        </div>
      </main>

      <ChatDrawer open={chatOpen} onToggle={() => setChatOpen((value) => !value)} />
      <RulesDialog open={rulesOpen} onClose={() => setRulesOpen(false)} />
      <SurrenderDialog
        open={surrenderOpen}
        onClose={() => setSurrenderOpen(false)}
        onConfirm={() => {
          setSurrenderOpen(false);
          setToast("演示对局已保留，不会真的投降");
        }}
      />
      <div className={`toast${toast ? " is-visible" : ""}`} role="status" aria-live="polite">{toast}</div>
    </div>
  );
}

function restoredSession(): SessionTicket | null {
  try {
    const raw = sessionStorage.getItem("armychess.session");
    return raw ? JSON.parse(raw) as SessionTicket : null;
  } catch {
    return null;
  }
}

export function App() {
  const [ticket, setTicket] = useState<SessionTicket | null>(() => restoredSession());
  const [demo, setDemo] = useState(() => new URLSearchParams(window.location.search).get("demo") === "1");
  if (demo) return <DemoGameScreen />;
  if (ticket) return <OnlineGame ticket={ticket} onLeave={() => { sessionStorage.removeItem("armychess.session"); setTicket(null); }} />;
  return <Lobby onEnter={setTicket} onDemo={() => setDemo(true)} />;
}
