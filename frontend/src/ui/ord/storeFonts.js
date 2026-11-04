const STORE_FONT = {
  "olive-rye": "store-font-greek",
  "island-pantry": "store-font-caribbean",
  "spice-route": "store-font-indian",
  "cedar-basket": "store-font-lebanese",
  "pearl-market": "store-font-chinese",
};

export function storeFontClass(storeId) {
  return STORE_FONT[storeId] || "";
}
