import { CheckCircle, Shuffle, WarningCircle } from "@phosphor-icons/react";
import type { LayoutRuleStatus } from "../game/layout";

interface LayoutPanelProps {
  status: LayoutRuleStatus;
  submitted: boolean;
  submittedCount: number;
  onRandomize: () => void;
  onSubmit: () => void;
}

const RULES: Array<{ key: keyof LayoutRuleStatus; label: string }> = [
  { key: "pieceCount", label: "25 枚棋子" },
  { key: "flagInHeadquarters", label: "军旗只在大本营" },
  { key: "bombsBehindFront", label: "炸弹不在第一排" },
  { key: "minesInBackRows", label: "地雷只在最后两排" },
  { key: "campsEmpty", label: "行营必须留空" },
];

export function LayoutPanel({ status, submitted, submittedCount, onRandomize, onSubmit }: LayoutPanelProps) {
  const valid = Object.values(status).every(Boolean);
  return (
    <aside className="layout-panel" aria-labelledby="layout-title">
      <span className="eyebrow">布阵阶段</span>
      <h2 id="layout-title">摆好你的 25 枚棋子</h2>
      <p className="layout-panel__hint">点击两枚己方棋子即可交换位置；所有规则通过后再提交。</p>
      <ul>
        {RULES.map((rule) => (
          <li key={rule.key} className={status[rule.key] ? "is-valid" : "is-invalid"}>
            {status[rule.key] ? <CheckCircle weight="fill" /> : <WarningCircle weight="fill" />}
            <span>{rule.label}</span>
          </li>
        ))}
      </ul>
      <div className="layout-panel__actions">
        <button type="button" className="button button--quiet" disabled={submitted} onClick={onRandomize}><Shuffle />重新随机</button>
        <button type="button" className="button button--primary" disabled={!valid || submitted} onClick={onSubmit}>{submitted ? "已提交布阵" : "确认布阵"}</button>
      </div>
      <small>{submitted ? `等待其他玩家 · ${submittedCount}/4 已提交` : "提交后由服务端再次校验，非法阵型无法开局。"}</small>
    </aside>
  );
}
