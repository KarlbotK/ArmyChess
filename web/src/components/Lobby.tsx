import { ArrowRight, CopySimple, Mountains, Play, UsersThree } from "@phosphor-icons/react";
import { useState, type FormEvent } from "react";
import type { SessionTicket } from "../network/rooms";
import { createRoom, joinRoom } from "../network/rooms";

export function Lobby({ onEnter, onDemo }: { onEnter: (ticket: SessionTicket) => void; onDemo: () => void }) {
  const [nickname, setNickname] = useState(() => localStorage.getItem("armychess.nickname") ?? "");
  const [roomCode, setRoomCode] = useState("");
  const [busy, setBusy] = useState<"create" | "join" | null>(null);
  const [error, setError] = useState("");

  const submit = async (action: "create" | "join") => {
    const name = nickname.trim();
    if (!name) { setError("先给自己取一个昵称"); return; }
    if (action === "join" && !/^\d{6}$/.test(roomCode.trim())) { setError("请输入 6 位房间号"); return; }
    setBusy(action);
    setError("");
    try {
      const ticket = action === "create" ? await createRoom(name) : await joinRoom(roomCode.trim(), name);
      localStorage.setItem("armychess.nickname", name);
      sessionStorage.setItem("armychess.session", JSON.stringify(ticket));
      onEnter(ticket);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "连接失败，请稍后重试");
    } finally {
      setBusy(null);
    }
  };

  const handleJoin = (event: FormEvent) => { event.preventDefault(); void submit("join"); };

  return (
    <main className="lobby-shell">
      <nav className="lobby-nav">
        <div className="brand"><Mountains weight="fill" /><span>四国军棋</span><em>Online</em></div>
        <button type="button" className="topbar-action" onClick={onDemo}><Play />体验棋盘</button>
      </nav>
      <section className="lobby-hero">
        <div className="lobby-copy">
          <span className="eyebrow">经典玩法 · 现代联机体验</span>
          <h1>四方落座，<br />再下一盘老军棋。</h1>
          <p>保留熟悉的暗棋、铁路与南北对东西玩法。创建一个私人房间，把六位房间号发给三位朋友即可开始。</p>
          <div className="lobby-promises">
            <span><UsersThree weight="fill" />四人实时对局</span>
            <span><CopySimple weight="fill" />刷新后恢复座位</span>
          </div>
        </div>
        <form className="lobby-card" onSubmit={handleJoin}>
          <div><span className="eyebrow">开始游戏</span><h2>加入一张牌桌</h2></div>
          <label>你的昵称<input value={nickname} maxLength={16} onChange={(event) => setNickname(event.target.value)} placeholder="例如：松风" autoComplete="nickname" /></label>
          <button className="lobby-primary" type="button" disabled={busy !== null} onClick={() => void submit("create")}>{busy === "create" ? "正在创建…" : "创建私人房间"}<ArrowRight weight="bold" /></button>
          <div className="lobby-divider"><span>或使用房间号</span></div>
          <div className="lobby-join-row">
            <label className="sr-only" htmlFor="room-code-input">六位房间号</label>
            <input id="room-code-input" value={roomCode} onChange={(event) => setRoomCode(event.target.value.replace(/\D/g, "").slice(0, 6))} inputMode="numeric" placeholder="输入 6 位房间号" />
            <button type="submit" disabled={busy !== null}>{busy === "join" ? "加入中…" : "加入"}</button>
          </div>
          {error && <p className="lobby-error" role="alert">{error}</p>}
          <small>无需注册。房间内不会展示“谁击败了谁”的棋子身份记录。</small>
        </form>
      </section>
    </main>
  );
}
