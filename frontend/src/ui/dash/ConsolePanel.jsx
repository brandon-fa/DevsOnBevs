import { useEffect, useRef } from "react";
import "./console.css";

const TYPE_LABELS = { error: "Error", success: "Success", info: "Info" };

export default function ConsolePanel({ messages }) {
  const log = useRef(null);

  useEffect(() => {
    if (log.current) log.current.scrollTop = log.current.scrollHeight;
  }, [messages.length]);

  return (
    <section className="dashboard-console dash-section" aria-labelledby="console-title">
      <h2 id="console-title">Console</h2>
      {messages.length === 0 && <p className="console-empty">Action feedback will appear here.</p>}
      <ol className="console-log" ref={log} role="log" aria-labelledby="console-title"
        aria-live="polite" aria-relevant="additions" aria-atomic="false" tabIndex={0}>
        {messages.map((message) => (
          <li key={message.id} className={`console-row is-${message.type}`}>
            <time dateTime={message.timestamp}>{new Date(message.timestamp).toLocaleTimeString()}</time>
            <span className="console-message-type">{TYPE_LABELS[message.type]}</span>
            <span>{message.text}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}
