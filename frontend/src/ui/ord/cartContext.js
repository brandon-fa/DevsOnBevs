import { createContext, createElement, useContext, useMemo, useState } from "react";

const CartContext = createContext(null);

export function CartProvider({ children }) {
  const [cart, setCart] = useState([]);
  const [store, setStore] = useState(null);

  const value = useMemo(() => ({ cart, setCart, store, setStore }), [cart, store]);

  return createElement(CartContext.Provider, { value }, children);
}

export function useCart() {
  const value = useContext(CartContext);
  if (!value) {
    throw new Error("useCart must be used within CartProvider.");
  }
  return value;
}
