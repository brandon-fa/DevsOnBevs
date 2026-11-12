import { useEffect, useState } from "react";
import { loadOperatorOrders, loadStores } from "../ord/orderApi.js";

// Loads once when the page opens; the operator screens stay static after that.
export default function useOperatorStores(onFeedback) {
  const [stores, setStores] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    async function load() {
      try {
        const [loadedStores, orders] = await Promise.all([
          loadStores(controller.signal),
          loadOperatorOrders(controller.signal),
        ]);
        if (controller.signal.aborted) return;
        setStores(loadedStores.map((store) => ({
          ...store,
          orders: orders.filter((order) => order.storeId === store.id),
        })));
      } catch (loadError) {
        if (controller.signal.aborted) return;
        let message = loadError.message || "Store data could not be loaded.";
        if (loadError instanceof TypeError) message = "Store data could not be loaded. Check your connection.";
        setError(message);
        onFeedback(message, "error");
      }
    }

    load();
    return () => controller.abort();
  }, [onFeedback]);

  function updateStore(storeId, changes) {
    setStores((current) => current.map((store) => (store.id === storeId ? { ...store, ...changes } : store)));
  }

  function updateOrder(orderId, changes) {
    setStores((current) => current.map((store) => ({
      ...store,
      orders: store.orders.map((order) => (order.orderId === orderId ? { ...order, ...changes } : order)),
    })));
  }

  return { stores, error, updateStore, updateOrder };
}
