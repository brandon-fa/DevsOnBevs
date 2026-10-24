// All API calls use the login cookie and return parsed JSON.
async function request(path, method = "GET", body = null, signal = null) {
  const options = {
    method: method,
    credentials: "include",
    cache: "no-store",
  };

  if (signal) {
    options.signal = signal;
  }
  if (body !== null) {
    options.headers = { "Content-Type": "application/json" };
    options.body = JSON.stringify(body);
  }

  const response = await fetch(path, options);
  let data = {};
  try {
    data = await response.json();
  } catch {
    // The caller will reject a successful response if required fields are missing.
  }

  if (!response.ok) {
    let message = "The request could not be completed.";
    if (data && typeof data.error === "string") {
      message = data.error;
    }
    throw new Error(message);
  }
  return data;
}

function isOrderResponse(order) {
  if (!order || !Number.isInteger(order.orderId)) return false;
  if (typeof order.status !== "string") return false;
  if (!Array.isArray(order.lines)) return false;
  if (!Array.isArray(order.transitionEvents)) return false;
  return Number.isFinite(order.subtotalCents);
}

export async function placeOrder(storeId, cart, destination) {
  try {
    const lines = [];
    for (const product of cart) {
      lines.push({ productId: product.id, quantity: product.quantity });
    }
    const body = {
      storeId: storeId,
      destination: destination.trim(),
      lines: lines,
    };
    const order = await request("/api/orders", "POST", body);
    if (!isOrderResponse(order)) {
      return { ok: false, error: "The server returned an unexpected response. Your order could not be confirmed." };
    }
    return { ok: true, order: order };
  } catch (error) {
    let message = error.message;
    if (error instanceof TypeError) {
      message = "The order could not be confirmed. Check your connection before trying again.";
    }
    return { ok: false, error: message };
  }
}

export async function loadStores(signal) {
  const city = await request("/api/city", "GET", null, signal);
  if (!city || !Array.isArray(city.groceryStores)) {
    throw new Error("Stores could not be loaded. Please try again.");
  }
  return city.groceryStores;
}

export async function loadCustomerOrders(signal) {
  const data = await request("/api/orders", "GET", null, signal);
  if (!data || !Array.isArray(data.orders)) {
    throw new Error("Your orders could not be loaded.");
  }
  for (const order of data.orders) {
    if (!isOrderResponse(order)) {
      throw new Error("Your order details could not be loaded. Please refresh your orders.");
    }
  }
  return data.orders;
}

export async function loadStoreOrders(signal) {
  const data = await request("/api/store/orders", "GET", null, signal);
  if (!data || !Array.isArray(data.pending) || !Array.isArray(data.preparing) || !Array.isArray(data.ready)) {
    throw new Error("Store orders could not be loaded.");
  }
  return data;
}

export function updateStoreOrder(orderId, action, reason = "") {
  let body = null;
  if (action === "reject") {
    body = { reason: reason.trim() };
  }
  const path = "/api/store/orders/" + orderId + "/" + action;
  return request(path, "POST", body);
}
