import { useState } from "react";
import "./reset-control.css";

// Reset: the operator restores the initial scenario (operator only, enforced again by the server).
export default function ResetControl({ onFeedback }) {
  const [confirming, setConfirming] = useState(false);
  const [resetting, setResetting] = useState(false);

  async function reset() {
    setResetting(true);
    onFeedback("Operator command: reset the simulator.", "info");
    try {
      const response = await fetch("/api/reset", { method: "POST", credentials: "include" });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(payload.error || "The simulator could not be reset.");
      }
      onFeedback(payload.message || "The simulator was reset.", "success");
    } catch (resetError) {
      onFeedback(resetError.message || "The simulator could not be reset.", "error");
    } finally {
      setResetting(false);
      setConfirming(false);
    }
  }

  return (
    <section className="dash-section reset-control" aria-labelledby="reset-title">
      <div className="section-heading">
        <h2 id="reset-title">Reset</h2>
        <p>Restore the initial scenario: every order is removed and stores return to their starting state.</p>
      </div>
      {confirming ? (
        <div className="reset-actions">
          <button type="button" className="button-primary" onClick={reset} disabled={resetting}>
            {resetting ? "Resetting…" : "Yes, reset everything"}
          </button>
          <button type="button" className="reset-cancel" onClick={() => setConfirming(false)} disabled={resetting}>
            Cancel
          </button>
        </div>
      ) : (
        <button type="button" className="button-primary" onClick={() => setConfirming(true)}>
          Reset simulator
        </button>
      )}
    </section>
  );
}