import { useEffect, useId, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ROLE_LABELS, dashboardPathForRole } from "../reg/accountFactory.js";
import { useCart } from "../ord/cartContext.js";
import { clearAccount } from "../reg/accountSession.js";
import { clearConsoleMessages } from "./consoleFeed.js";

export default function SiteHeader({ account }) {
  const navigate = useNavigate();
  const menuId = useId();
  const cluster = useRef(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return undefined;

    function onPointerDown(event) {
      if (cluster.current && !cluster.current.contains(event.target)) setOpen(false);
    }
    function onKeyDown(event) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  async function signOut() {
    setOpen(false);
    try {
      await fetch("/api/logout", { method: "POST", credentials: "include" });
    } catch {
      // Still clear the local session when the server cannot be reached.
    } finally {
      clearConsoleMessages();
      clearAccount();
      navigate("/login");
    }
  }

  function go(path) {
    setOpen(false);
    navigate(path);
  }

  const { cart } = useCart();
  const cartCount = account.role === "customer"
    ? cart.reduce((total, line) => total + line.quantity, 0)
    : null;
  const home = dashboardPathForRole(account.role);

  return (
    <header className="site-header">
      <div className="site-header-inner">
        <button className="wordmark wordmark-button" type="button" onClick={() => navigate(home)}>
          Little Isles
        </button>
        <div className="account-cluster">
          <div className="account-identity" ref={cluster}>
            <button
              className="account-name account-menu-button"
              type="button"
              aria-haspopup="menu"
              aria-expanded={open}
              aria-controls={menuId}
              onClick={() => setOpen((current) => !current)}
            >
              <span className="role-badge">{ROLE_LABELS[account.role]}:</span>
              <span className="account-name-text">
                <svg className="account-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="12" cy="8" r="3.2" />
                  <path d="M5.5 19.2c1.2-3 3.5-4.5 6.5-4.5s5.3 1.5 6.5 4.5" />
                </svg>
                {account.fullName}
              </span>
            </button>
            {open && (
              <div className="account-menu" id={menuId} role="menu">
                {account.role === "customer" && (
                  <button className="account-menu-item" type="button" role="menuitem" onClick={() => go(`${home}/orders`)}>
                    Track order
                  </button>
                )}
                <button className="account-menu-item" type="button" role="menuitem" onClick={() => go(`${home}/account`)}>
                  Manage account
                </button>
                <button className="account-menu-item" type="button" role="menuitem" onClick={() => go(`${home}/history`)}>
                  History
                </button>
                <button className="account-menu-item" type="button" role="menuitem" onClick={signOut}>
                  Sign out
                </button>
              </div>
            )}
          </div>
          {cartCount !== null && (
            <button
              className="cart-button"
              type="button"
              aria-label={`Cart, ${cartCount} items`}
              onClick={() => navigate(`${home}/cart`)}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 7h15l-1.5 9h-12z" />
                <path d="M6 7 5 4H2" />
                <circle cx="9" cy="20" r="1.3" />
                <circle cx="18" cy="20" r="1.3" />
              </svg>
              {cartCount > 0 && <span className="cart-count">{cartCount}</span>}
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
