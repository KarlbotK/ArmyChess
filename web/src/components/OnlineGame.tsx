import { BookOpen, Check, CopySimple, Flag, Mountains, WifiHigh } from "@phosphor-icons/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { keyOf, legalMoveOptions, type BoardPiece } from "../game/board";
import {
  createDefaultLayout,
  fromViewer,
  LayoutRuleViolation,
  rotateForViewer,
  swapLayoutPieces,
  validateLayout,
  type LayoutPlacement,
} from "../game/layout";
import { publicEventText } from "../game/publicEvents";
import { PLAYER_META, type Coordinate, type GameSnapshot, type PieceView, type PlayerId } from "../game/types";
import { GameSocket } from "../network/gameSocket";
import type { RoomPlayer } from "../network/protocol";
import { socketUrl, type SessionTicket } from "../network/rooms";
import { ActivityPanel, type ActivityItem } from "./ActivityPanel";
import { GameBoard } from "./GameBoard";
import { LayoutPanel } from "./LayoutPanel";
import { ChatDrawer, ResultDialog, RulesDialog, SurrenderDialog } from "./Overlays";
import { PlayerPlate } from "./PlayerPlate";

const SEATS = ["south", "west", "north", "east"] as const;

function timeNow() {
  return new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date());
}

function emptySnapshot(viewer: PlayerId): GameSnapshot {
  return {
    phase: "LAYOUT", viewer, currentTurn: 0, revision: 0, pieces: [], turnDeadlineEpochMs: null,
    alive: [true, true, true, true], timeoutCounts: [0, 0, 0, 0], winnerTeam: null, rematchVotes: 0,
  };
}

export function OnlineGame({ ticket, onLeave }: { ticket: SessionTicket; onLeave: () => void }) {
  const socketRef = useRef<GameSocket | null>(null);
  const snapshotRef = useRef<GameSnapshot>(emptySnapshot(ticket.playerId));
  const layoutRequestRef = useRef<string | null>(null);
  const [snapshot, setSnapshot] = useState(() => emptySnapshot(ticket.playerId));
  const [layout, setLayout] = useState<LayoutPlacement[]>(() => createDefaultLayout(ticket.playerId));
  const layoutIds = useRef(Array.from({ length: 25 }, () => `layout-${crypto.randomUUID()}`));
  const [submitted, setSubmitted] = useState(false);
  const [players, setPlayers] = useState<RoomPlayer[]>([{ playerId: ticket.playerId, nickname: ticket.nickname }]);
  const playersRef = useRef(players);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [legalMoves, setLegalMoves] = useState<Coordinate[]>([]);
  const [legalRoutes, setLegalRoutes] = useState<Record<string, Coordinate[]>>({});
  const [activity, setActivity] = useState<ActivityItem[]>([]);
  const [chatMessages, setChatMessages] = useState<string[]>([]);
  const [rulesOpen, setRulesOpen] = useState(false);
  const [surrenderOpen, setSurrenderOpen] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [rematchRequested, setRematchRequested] = useState(false);
  const [clock, setClock] = useState(Date.now());
  const [toast, setToast] = useState("正在连接牌桌…");
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const socket = new GameSocket(socketUrl(ticket));
    socketRef.current = socket;
    const unsubscribe = socket.subscribe((message) => {
      if (message.type === "SESSION_READY") {
        setConnected(true);
        setPlayers(message.players);
        playersRef.current = message.players;
        setToast("已恢复到你的座位");
      }
      if (message.type === "ROOM_STATE") {
        setPlayers(message.players);
        playersRef.current = message.players;
      }
      if (message.type === "SNAPSHOT") {
        const previous = snapshotRef.current;
        const newlyRevealedFlag = message.snapshot.pieces.find((piece) =>
          piece.owner !== ticket.playerId
          && piece.visibleType === "FLAG"
          && !previous.pieces.some((oldPiece) => oldPiece.id === piece.id && oldPiece.visibleType === "FLAG"),
        );
        snapshotRef.current = message.snapshot;
        setSnapshot(message.snapshot);
        if (previous.currentTurn !== message.snapshot.currentTurn || previous.phase !== message.snapshot.phase) {
          setSelectedId(null);
          setLegalMoves([]);
          setLegalRoutes({});
        }
        if (previous.phase === "FINISHED" && message.snapshot.phase === "LAYOUT") {
          setSubmitted(false);
          setRematchRequested(false);
          setLayout(createDefaultLayout(ticket.playerId));
          setActivity([]);
          setToast("新一局开始，请重新布阵");
        }
        if (message.snapshot.phase === "LAYOUT" && message.snapshot.pieces.filter(({ owner }) => owner === ticket.playerId).length === 25) {
          setSubmitted(true);
        }
        if (newlyRevealedFlag) {
          const direction = PLAYER_META[newlyRevealedFlag.owner].direction;
          setActivity((items) => [{ id: `flag-${message.snapshot.revision}`, text: `${direction}军旗已亮出`, time: timeNow(), tone: "neutral" }, ...items]);
          setToast(`${direction}司令阵亡，军旗已亮出`);
        } else if (message.snapshot.phase === "PLAYING") setToast("对局已同步");
      }
      if (message.type === "PUBLIC_EVENT") {
        const text = publicEventText(message.event);
        setActivity((items) => [{
          id: `${message.event.at}-${message.event.type}`,
          text,
          time: timeNow(),
          tone: message.event.actor === 1 ? "blue" : message.event.actor === 3 ? "violet" : "coral",
        }, ...items]);
      }
      if (message.type === "CHAT_MESSAGE") {
        const name = playersRef.current.find(({ playerId }) => playerId === message.player)?.nickname ?? PLAYER_META[message.player].direction;
        setChatMessages((items) => [...items.slice(-29), `${message.player === ticket.playerId ? "我" : name}：${message.text}`]);
      }
      if (message.type === "ACTION_REJECTED") {
        if (message.requestId === layoutRequestRef.current) {
          layoutRequestRef.current = null;
          setSubmitted(false);
        }
        setToast(message.message);
      }
    });
    socket.connect();
    return () => { unsubscribe(); socket.close(); socketRef.current = null; };
  }, [ticket]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(""), 2_800);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (snapshot.phase !== "PLAYING" || snapshot.turnDeadlineEpochMs === null) return;
    setClock(Date.now());
    const timer = window.setInterval(() => setClock(Date.now()), 250);
    return () => window.clearInterval(timer);
  }, [snapshot.phase, snapshot.turnDeadlineEpochMs]);

  const snapshotHasOwnLayout = snapshot.phase === "LAYOUT"
    && snapshot.pieces.filter(({ owner }) => owner === ticket.playerId).length === 25;
  const layoutPieces = useMemo<PieceView[]>(() => layout.map((placement, index) => ({
    id: layoutIds.current[index],
    owner: ticket.playerId,
    position: placement.position,
    visibleType: placement.type,
    revealed: true,
  })), [layout, ticket.playerId]);
  const visiblePieces = snapshot.phase === "LAYOUT" && !snapshotHasOwnLayout
    ? [...snapshot.pieces, ...layoutPieces]
    : snapshot.pieces;
  const displayPieces = useMemo(() => visiblePieces.map((piece) => ({
    ...piece,
    position: rotateForViewer(piece.position, ticket.playerId),
  })), [visiblePieces, ticket.playerId]);
  const layoutStatus = useMemo(() => validateLayout(ticket.playerId, layout), [layout, ticket.playerId]);
  const submittedCount = new Set(snapshot.pieces.map(({ owner }) => owner)).size;
  const secondsRemaining = snapshot.turnDeadlineEpochMs === null
    ? null
    : Math.max(0, Math.ceil((snapshot.turnDeadlineEpochMs - clock) / 1_000));
  const viewerTeam = ticket.playerId % 2 === 0 ? "NORTH_SOUTH" : "EAST_WEST";
  const winnerLabel = snapshot.winnerTeam === "NORTH_SOUTH" ? "南北队" : "东西队";

  const handleSelect = (pieceId: string) => {
    if (snapshot.phase === "LAYOUT") {
      if (submitted) { setToast("布阵已经提交，正在等待其他玩家"); return; }
      const nextIndex = layoutIds.current.indexOf(pieceId);
      if (nextIndex < 0) { setToast("只能调整自己的棋子"); return; }
      if (!selectedId) {
        setSelectedId(pieceId);
        setToast("再选择一枚棋子交换位置");
        return;
      }
      const selectedIndex = layoutIds.current.indexOf(selectedId);
      if (selectedIndex < 0 || selectedIndex === nextIndex) {
        setSelectedId(null);
        return;
      }
      setSelectedId(null);
      try {
        const next = swapLayoutPieces(ticket.playerId, layout, selectedIndex, nextIndex);
        setLayout(next);
        setToast("位置已交换");
      } catch (error) {
        if (error instanceof LayoutRuleViolation) {
          setToast(`${error.message}，已禁止交换`);
          return;
        }
        throw error;
      }
      return;
    }
    if (snapshot.phase !== "PLAYING") return;
    if (snapshot.currentTurn !== ticket.playerId) {
      setSelectedId(null);
      setLegalMoves([]);
      setLegalRoutes({});
      setToast(`现在是${PLAYER_META[snapshot.currentTurn].direction}回合`);
      return;
    }
    const piece = snapshot.pieces.find(({ id }) => id === pieceId);
    if (!piece || piece.owner !== ticket.playerId || !piece.visibleType) { setToast("暗棋身份不可查看"); return; }
    const board = snapshot.pieces.map<BoardPiece>((candidate) => ({
      id: candidate.id,
      owner: candidate.owner,
      type: candidate.visibleType ?? "CAPTAIN",
      position: candidate.position,
    }));
    const options = legalMoveOptions({ id: piece.id, owner: piece.owner, type: piece.visibleType, position: piece.position }, board);
    const rawMoves = options.map(({ destination }) => destination);
    const displayRoutes = piece.visibleType === "SAPPER"
      ? Object.fromEntries(options.map(({ destination, route }) => {
        const displayDestination = rotateForViewer(destination, ticket.playerId);
        return [keyOf(displayDestination), route.map((point) => rotateForViewer(point, ticket.playerId))];
      }))
      : {};
    setSelectedId(pieceId);
    setLegalMoves(rawMoves.map((move) => rotateForViewer(move, ticket.playerId)));
    setLegalRoutes(displayRoutes);
    setToast(rawMoves.length
      ? piece.visibleType === "SAPPER" ? `可走 ${rawMoves.length} 个位置，悬停落点查看路线` : `可走 ${rawMoves.length} 个位置`
      : "这枚棋子当前无法移动");
  };

  const handleMove = (displayPosition: Coordinate) => {
    if (snapshot.phase !== "PLAYING") return;
    if (!selectedId || !legalMoves.some((move) => keyOf(move) === keyOf(displayPosition))) return;
    socketRef.current?.send({
      type: "MOVE_REQUEST",
      pieceId: selectedId,
      to: fromViewer(displayPosition, ticket.playerId),
      expectedRevision: snapshot.revision,
    });
    setSelectedId(null);
    setLegalMoves([]);
    setLegalRoutes({});
  };

  const randomizeLayout = () => {
    setLayout(createDefaultLayout(ticket.playerId, true));
    setSelectedId(null);
    setToast("已生成一套新的合法阵型");
  };

  const submitLayout = () => {
    if (players.length !== 4 || submitted) return;
    if (!Object.values(layoutStatus).every(Boolean)) { setToast("还有布阵规则未满足"); return; }
    layoutRequestRef.current = socketRef.current?.send({ type: "SUBMIT_LAYOUT", placements: layout }) ?? null;
    setSubmitted(true);
    setSelectedId(null);
    setToast("布阵已提交，等待其他玩家");
  };

  const copyRoomCode = async () => {
    await navigator.clipboard.writeText(ticket.roomCode).catch(() => undefined);
    setCopied(true);
    setToast("房间号已复制");
    window.setTimeout(() => setCopied(false), 1_800);
  };

  const requestRematch = () => {
    if (rematchRequested || snapshot.phase !== "FINISHED") return;
    socketRef.current?.send({ type: "REMATCH_REQUEST" });
    setRematchRequested(true);
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="brand brand-button" type="button" onClick={onLeave} aria-label="返回大厅"><Mountains weight="fill" /><span>四国军棋</span><em>Online</em></button>
        <button className="room-code" type="button" onClick={copyRoomCode}><span>房间号：{ticket.roomCode}</span>{copied ? <Check weight="bold" /> : <CopySimple />}</button>
        <div className="turn-status" aria-live="polite">
          <span>{snapshot.phase === "LAYOUT" ? players.length < 4 ? `等待玩家 ${players.length}/4` : "布阵阶段" : snapshot.phase === "FINISHED" ? "对局结束" : snapshot.currentTurn === ticket.playerId ? "你的回合" : `${PLAYER_META[snapshot.currentTurn].direction}回合`}</span>
          <small>南 → 西 → 北 → 东</small>
          {snapshot.phase === "PLAYING" && secondsRemaining !== null && <strong className={secondsRemaining <= 5 ? "is-urgent" : ""}>00:{String(secondsRemaining).padStart(2, "0")}</strong>}
        </div>
        <div className="topbar__meta"><span className="network-state"><WifiHigh weight="bold" />{connected ? " 已连接" : " 重连中"}</span><button className="topbar-action" type="button" onClick={() => setRulesOpen(true)}><BookOpen />规则</button><span className="topbar-divider" /><button className="topbar-action" type="button" onClick={() => setSurrenderOpen(true)} disabled={snapshot.phase !== "PLAYING"}><Flag />投降</button></div>
      </header>
      <main className="game-surface">
        <ActivityPanel items={activity.length ? activity : [{ id: "waiting", text: "等待公开战况", time: "--:--", tone: "neutral" }]} />
        <section className="board-stage" aria-label="当前对局">
          {([0, 1, 2, 3] as PlayerId[]).map((player) => {
            const seat = SEATS[(player - ticket.playerId + 4) % 4];
            const joined = players.find(({ playerId }) => playerId === player);
            return <PlayerPlate key={player} player={player} position={seat} name={joined?.nickname ?? "等待加入"} self={player === ticket.playerId} active={snapshot.currentTurn === player && snapshot.phase === "PLAYING"} online={Boolean(joined)} />;
          })}
          <GameBoard pieces={displayPieces} selectedId={selectedId} legalMoves={legalMoves} legalRoutes={legalRoutes} onSelect={handleSelect} onMove={handleMove} />
          {snapshot.phase === "LAYOUT" && players.length < 4 && <div className="waiting-card"><span className="eyebrow">私人房间 {ticket.roomCode}</span><h2>等待朋友落座</h2><p>已有 {players.length} 位玩家。四人到齐后进入布阵，所有人确认阵型才会开局。</p></div>}
          {snapshot.phase === "LAYOUT" && players.length === 4 && <LayoutPanel status={layoutStatus} submitted={submitted} submittedCount={submittedCount} onRandomize={randomizeLayout} onSubmit={submitLayout} />}
        </section>
        <div className="team-note"><span className="team-note__mark">✦</span><span><strong>{PLAYER_META[ticket.playerId].team}</strong><small>与你的对家并肩作战</small></span></div>
      </main>
      <ChatDrawer open={chatOpen} onToggle={() => setChatOpen((value) => !value)} messages={chatMessages} onSend={(text) => socketRef.current?.send({ type: "CHAT_SEND", text })} />
      <RulesDialog open={rulesOpen} onClose={() => setRulesOpen(false)} />
      <SurrenderDialog open={surrenderOpen} onClose={() => setSurrenderOpen(false)} onConfirm={() => { socketRef.current?.send({ type: "SURRENDER_REQUEST" }); setSurrenderOpen(false); }} />
      <ResultDialog open={snapshot.phase === "FINISHED"} won={snapshot.winnerTeam === viewerTeam} winnerLabel={winnerLabel} requested={rematchRequested} voteCount={snapshot.rematchVotes} onRematch={requestRematch} onLeave={onLeave} />
      <div className={`toast${toast ? " is-visible" : ""}`} role="status">{toast}</div>
    </div>
  );
}
