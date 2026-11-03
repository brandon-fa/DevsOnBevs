import { useEffect, useState } from "react";
import { saveAccount } from "../reg/accountSession.js";

export default function useSessionAccount() {
  const [account, setAccount] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    async function loadAccount() {
      try {
        const response = await fetch("/api/session", { credentials: "include" });
        if (response.ok) {
          const currentAccount = await response.json();
          if (active) {
            saveAccount(currentAccount);
            setAccount(currentAccount);
          }
        }
      } catch {
        // An unavailable or expired session returns the user to login.
      } finally {
        if (active) setLoading(false);
      }
    }
    loadAccount();
    return () => { active = false; };
  }, []);

  return { account, setAccount, loading };
}
