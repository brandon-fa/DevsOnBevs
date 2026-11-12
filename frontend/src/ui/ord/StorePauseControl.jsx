import { useState } from "react";
import { pauseStore } from "./orderApi.js";

// The store's staff can pause their own store; only the platform operator can reopen it.
export default function StorePauseControl({ storeId, storeName, status, onPaused, onFeedback }) {
    const [confirming, setConfirming] = useState(false);
    const [pausing, setPausing] = useState(false);

    async function pause() {
        setPausing(true);
        onFeedback("Store command: pause " + storeName + ".", "info");
        try {
            const result = await pauseStore(storeId);
            onFeedback(result.message || storeName + " is paused.", "success");
            onPaused(result.status);
        } catch (error) {
            onFeedback(error.message || "The store could not be paused.", "error");
        } finally {
            setPausing(false);
            setConfirming(false);
        }
    }

    if (status === "closed") {
        return <p className="store-status-note">This store is closed.</p>;
    }
    if (status === "paused") {
        return (
            <p className="store-status-note is-paused">
                Paused: you are not taking new orders, but you still finish the ones you accepted.
                Only the platform operator can reopen your store.
            </p>
        );
    }
    if (confirming) {
        return (
            <div className="store-pause-actions">
                <button type="button" className="button-primary" onClick={pause} disabled={pausing}>
                    {pausing ? "Pausing…" : "Yes, pause new orders"}
                </button>
                <button type="button" className="store-pause-cancel" onClick={() => setConfirming(false)} disabled={pausing}>
                    Cancel
                </button>
            </div>
        );
    }
    return (
        <button type="button" className="button-primary" onClick={() => setConfirming(true)}>
            Pause store
        </button>
    );
}