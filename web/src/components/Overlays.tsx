import { ChatCircleDots, PaperPlaneTilt, X } from "@phosphor-icons/react";
import { useState, type FormEvent } from "react";

interface RulesDialogProps {
  open: boolean;
  onClose: () => void;
}

export function RulesDialog({ open, onClose }: RulesDialogProps) {
  if (!open) return null;
  return (
    <div className="overlay" role="presentation" onMouseDown={onClose}>
      <section className="dialog rules-dialog" role="dialog" aria-modal="true" aria-labelledby="rules-title" onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog__header">
          <div>
            <span className="eyebrow">对局说明</span>
            <h2 id="rules-title">四国军棋规则</h2>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="关闭规则"><X /></button>
        </div>
        <div className="rules-grid">
          <article><strong>四方轮转</strong><p>南家 → 西家 → 北家 → 东家依次执棋；南北为一队，东西为一队，不能攻击队友。每步 30 秒，前 4 次超时自动跳过，累计第 5 次全军覆没。</p></article>
          <article><strong>布阵限制</strong><p>每方 25 枚。军旗只能放在两个大本营之一；地雷只能放最后两排；炸弹不能放第一排；行营必须留空。</p></article>
          <article><strong>大小顺序</strong><p>司令、军长、师长、旅长、团长、营长、连长、排长、工兵依次由大到小；同级相遇同归于尽。</p></article>
          <article><strong>特殊棋子</strong><p>工兵可以排雷；其他活动棋子碰地雷会阵亡；炸弹与任何棋子相遇都同归于尽；军旗、地雷不能移动。</p></article>
          <article><strong>铁路与行营</strong><p>普通棋子沿无阻挡铁路直行；工兵可在连通铁路上任意转弯、绕行，但不能穿过任何棋子。进入行营的棋子不能被攻击；大本营内的棋子不能再移动。</p></article>
          <article><strong>司令与军旗</strong><p>司令阵亡后，本方军旗立即亮出；军旗被擒获或无棋可走时，该方全军覆没并退出本局。</p></article>
          <article><strong>胜负</strong><p>夺取敌方军旗或使对方两位玩家全部出局即可获胜。</p></article>
          <article><strong>公开信息</strong><p>棋盘会反映棋子存亡和规则要求的亮旗，但不会生成“谁击败了谁”的身份记录。</p></article>
        </div>
        <div className="privacy-note">
          <strong>暗棋原则</strong>
          <p>系统不会生成“谁击败了谁”的记录；公开战况只显示移动、交锋或公开的全军覆没原因。</p>
        </div>
      </section>
    </div>
  );
}

interface ConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export function SurrenderDialog({ open, onClose, onConfirm }: ConfirmDialogProps) {
  if (!open) return null;
  return (
    <div className="overlay" role="presentation" onMouseDown={onClose}>
      <section className="dialog confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="surrender-title" onMouseDown={(event) => event.stopPropagation()}>
        <span className="eyebrow">请确认</span>
        <h2 id="surrender-title">要离开这局吗？</h2>
        <p>投降后你的棋子会退出棋盘，本局无法撤销。</p>
        <div className="dialog__actions">
          <button className="button button--quiet" type="button" onClick={onClose}>继续对局</button>
          <button className="button button--danger" type="button" onClick={onConfirm}>确认投降</button>
        </div>
      </section>
    </div>
  );
}

interface ResultDialogProps {
  open: boolean;
  won: boolean;
  winnerLabel: string;
  requested: boolean;
  voteCount: number;
  onRematch: () => void;
  onLeave: () => void;
}

export function ResultDialog({ open, won, winnerLabel, requested, voteCount, onRematch, onLeave }: ResultDialogProps) {
  if (!open) return null;
  return (
    <div className="overlay result-overlay" role="presentation">
      <section className="dialog result-dialog" role="dialog" aria-modal="true" aria-labelledby="result-title">
        <span className="eyebrow">本局结束</span>
        <div className="result-dialog__mark" aria-hidden="true">{won ? "胜" : "负"}</div>
        <h2 id="result-title">{won ? "并肩取胜" : "胜负已定"}</h2>
        <p>{winnerLabel}赢得本局。暗棋身份仍然保密，对局记录不会显示谁击败了谁。</p>
        <div className="result-dialog__votes">再来一局：{voteCount}/4 已准备</div>
        <div className="dialog__actions">
          <button className="button button--quiet" type="button" onClick={onLeave}>返回大厅</button>
          <button className="button button--primary" type="button" onClick={onRematch} disabled={requested}>{requested ? "等待其他玩家" : "再来一局"}</button>
        </div>
      </section>
    </div>
  );
}

interface ChatDrawerProps {
  open: boolean;
  onToggle: () => void;
  messages?: string[];
  onSend?: (text: string) => void;
}

export function ChatDrawer({ open, onToggle, messages: providedMessages, onSend }: ChatDrawerProps) {
  const [localMessages, setLocalMessages] = useState(["清风：右路我来守。", "星河：收到，准备推进。"]);
  const [draft, setDraft] = useState("");
  const messages = providedMessages ?? localMessages;
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const value = draft.trim().slice(0, 80);
    if (!value) return;
    if (onSend) onSend(value);
    else setLocalMessages((current) => [...current, `我：${value}`]);
    setDraft("");
  };
  return (
    <section className={`chat-drawer${open ? " is-open" : ""}`} aria-label="对局聊天">
      <button className="chat-drawer__handle" type="button" onClick={onToggle} aria-expanded={open}>
        <ChatCircleDots aria-hidden="true" />
        <span>{open ? "收起聊天" : "点击展开聊天"}</span>
      </button>
      {open && (
        <div className="chat-drawer__body">
          <div className="chat-messages" aria-live="polite">
            {messages.map((message, index) => <p key={`${message}-${index}`}>{message}</p>)}
          </div>
          <form onSubmit={submit}>
            <label className="sr-only" htmlFor="chat-input">发送聊天消息</label>
            <input id="chat-input" value={draft} onChange={(event) => setDraft(event.target.value)} maxLength={80} placeholder="和队友聊一聊…" />
            <button type="submit" aria-label="发送"><PaperPlaneTilt weight="fill" /></button>
          </form>
        </div>
      )}
    </section>
  );
}
