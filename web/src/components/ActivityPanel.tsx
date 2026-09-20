import { CrosshairSimple } from "@phosphor-icons/react";

export interface ActivityItem {
  id: string;
  text: string;
  time: string;
  tone: "coral" | "blue" | "violet" | "neutral";
}

export function ActivityPanel({ items }: { items: ActivityItem[] }) {
  return (
    <aside className="activity-panel" aria-labelledby="activity-title">
      <div className="activity-panel__heading">
        <CrosshairSimple aria-hidden="true" />
        <h2 id="activity-title">最近战况</h2>
      </div>
      <ol>
        {items.slice(0, 4).map((item) => (
          <li key={item.id}>
            <span className={`activity-dot activity-dot--${item.tone}`} aria-hidden="true" />
            <span>{item.text}</span>
            <time>{item.time}</time>
          </li>
        ))}
      </ol>
      <p>战况只记录公开动作，不展示交战棋子身份。</p>
    </aside>
  );
}
