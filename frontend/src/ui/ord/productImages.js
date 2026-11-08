const PRODUCT_IMAGES = {
  "green olives": "/products/olives.avif",
  halloumi: "/products/halloumi.png",
  "sesame bread": "/products/sesame-bread.avif",
  "scotch bonnet peppers": "/products/peppers.webp",
  "green seasoning": "/products/seasoning.jpg",
  atta: "/products/atta.jpg",
  "basmati rice": "/products/basmati.jpg",
  labneh: "/products/labneh.jpeg",
  pita: "/products/pita.jpg",
  "jasmine rice": "/products/jasmine.avif",
  "bok choy": "/products/bok-choy.jpg",
};

export function productImage(product) {
  const name = typeof product?.name === "string" ? product.name.trim().toLowerCase() : "";
  return PRODUCT_IMAGES[name] || product?.image || "";
}
