export async function placeOrder(storeId, cart) {
  const response = await fetch("/api/orders", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      storeId: storeId,
      lines: cart.map((line) => ({ itemId: line.id, quantity: line.quantity })),
    }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    return { ok: false, error: data.error || "The order could not be placed." };
  }
  return { ok: true, order: data };
}