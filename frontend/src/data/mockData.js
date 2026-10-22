export const stores = [
  {
    id: 1,
    name: "Olive & Rye",
    mark: "OR",
    cuisine: "Mediterranean",
    neighborhood: "Plateau-Mont-Royal",
    goods: "Olives, halloumi, and sesame bread",
  },
  {
    id: 2,
    name: "Island Pantry",
    mark: "IP",
    cuisine: "Caribbean",
    neighborhood: "Notre-Dame-de-Grâce",
    goods: "Peppers, green seasoning, and plantains",
  },
  {
    id: 3,
    name: "Spice Route",
    mark: "SR",
    cuisine: "South Asian",
    neighborhood: "Parc-Extension",
    goods: "Atta, garam masala, and basmati rice",
  },
  {
    id: 4,
    name: "Cedar Basket",
    mark: "CB",
    cuisine: "Levantine",
    neighborhood: "Mile End",
    goods: "Labneh, pita, and green olives",
  },
  {
    id: 5,
    name: "Pearl Market",
    mark: "PM",
    cuisine: "East Asian",
    neighborhood: "Chinatown",
    goods: "Jasmine rice, Shaoxing wine, and bok choy",
  },
];

export const ORDER_STEPS = [
  ["placed", "Placed"],
  ["accepted", "Accepted"],
  ["preparing", "Preparing"],
  ["ready", "Ready"],
  ["picked_up", "Picked up"],
  ["delivered", "Delivered"],
];

export const STATUS_LABELS = {
  placed: "Placed",
  accepted: "Accepted",
  preparing: "Preparing",
  ready: "Ready",
  picked_up: "Picked up",
  delivered: "Delivered",
};

export const activeOrder = {
  id: 1042,
  customerName: "Demo Customer",
  store: "Cedar Basket",
  status: "picked_up",
  courierId: "nadia",
  courier: "Nadia Pelletier",
  address: "418 Avenue Duluth, Montreal",
  eta: "6:40 p.m.",
  items: [
    { name: "Labneh", quantity: 1, priceCents: 695 },
    { name: "Pita", quantity: 1, priceCents: 450 },
    { name: "Green olives", quantity: 1, priceCents: 350 },
  ],
  deliveryFeeCents: 399,
  totalCents: 1894,
};

export const recentOrders = [
  {
    id: 1038,
    store: "Island Pantry",
    date: "12 Oct 2026",
    status: "delivered",
    totalCents: 1937,
  },
  {
    id: 1031,
    store: "Spice Route",
    date: "8 Oct 2026",
    status: "delivered",
    totalCents: 1748,
  },
  {
    id: 1024,
    store: "Olive & Rye",
    date: "2 Oct 2026",
    status: "delivered",
    totalCents: 1939,
  },
];

export function formatMoney(cents) {
  return new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: "CAD",
  }).format(cents / 100);
}
