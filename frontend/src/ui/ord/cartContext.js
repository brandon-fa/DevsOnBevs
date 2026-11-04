import { createContext, createElement, useContext, useMemo, useState } from "react";

const CartContext = createContext(null);

export function CartProvider({ children }) {
  const [baskets, setBaskets] = useState([]);

  const cart = baskets.flatMap((basket) => basket.lines);
  const value = useMemo(() => ({ baskets, setBaskets, cart }), [baskets, cart]);

  return createElement(CartContext.Provider, { value }, children);
}

export function useCart() {
  const value = useContext(CartContext);
  if (!value) {
    throw new Error("useCart must be used within CartProvider.");
  }
  return value;
}
