import { useEffect, useRef, useState } from "react";
import "./console.css";

const TYPE_LABELS = { error: "Error", success: "Success", info: "Info" };
const PAGE_SIZE = 8;

export default function ConsolePanel({ messages }) {
  const newestFirst = [...messages].reverse();
  const pageCount = Math.max(1, Math.ceil(newestFirst.length / PAGE_SIZE));
  const [page, setPage] = useState(0);
  const followingLatest = useRef(true);

  useEffect(() => {
    const last = pageCount - 1;
    setPage((current) => (followingLatest.current ? 0 : Math.min(current, last)));
  }, [pageCount]);

  function showPage(next) {
    const last = pageCount - 1;
    const value = Math.max(0, Math.min(last, next));
    followingLatest.current = value === 0;
    setPage(value);
  }

  const start = page * PAGE_SIZE;
  const visible = newestFirst.slice(start, start + PAGE_SIZE);
  const rangeEnd = newestFirst.length === 0 ? 0 : Math.min(start + PAGE_SIZE, newestFirst.length);

  return (
    <section className="dashboard-console dash-section" aria-label="Action history">
      {newestFirst.length === 0 && <p className="console-empty">Action feedback will appear here.</p>}
      <ol className="console-log" start={start + 1} role="log" aria-label="Action history"
        aria-live="polite" aria-relevant="additions" aria-atomic="false">
        {visible.map((message) => (
          <li key={message.id} className={`console-row is-${message.type}`}>
            <time dateTime={message.timestamp}>{new Date(message.timestamp).toLocaleTimeString()}</time>
            <span className="console-message-type">{TYPE_LABELS[message.type]}</span>
            <span>{message.text}</span>
          </li>
        ))}
      </ol>
      {newestFirst.length > PAGE_SIZE && (
        <div className="console-pager">
          <button
            className="console-pager-button"
            type="button"
            aria-label="Newer actions"
            disabled={page === 0}
            onClick={() => showPage(page - 1)}
          >
            <span aria-hidden="true">←</span>
          </button>
          <p className="console-pager-status">
            {start + 1}–{rangeEnd} of {newestFirst.length}
          </p>
          <button
            className="console-pager-button"
            type="button"
            aria-label="Older actions"
            disabled={page >= pageCount - 1}
            onClick={() => showPage(page + 1)}
          >
            <span aria-hidden="true">→</span>
          </button>
        </div>
      )}
    </section>
  );
}
