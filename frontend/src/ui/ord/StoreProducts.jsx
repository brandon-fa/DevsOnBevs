import { formatMoney } from "../../data/mockData.js";
import { productImage } from "./productImages.js";

export default function StoreProducts({ store, cart = [], onAdd, disabled = false }) {
  function quantityInCart(productId) {
    for (const line of cart) {
      if (line.id === productId) return line.quantity;
    }
    return 0;
  }

  return (
    <section className="store-products" aria-labelledby="store-products-title">
      <h3 id="store-products-title" className={store.fontClass}>{store.name}</h3>
      {store.cuisine && <p className="store-cuisine">{store.cuisine}</p>}
      <p className={`store-open ${store.open ? "is-open" : "is-closed"}`}>
        {store.open ? "Open" : "Closed"}
      </p>
      <ul className="product-list">
        {store.products.map((product) => {
          const quantity = quantityInCart(product.id);
          const image = productImage(product);
          return (
          <li key={product.id} className="product-card">
            {image && (
              <img className="product-image" src={image} alt="" />
            )}
            <p className="product-name">
              {product.name}
              {quantity > 0 && <span className="product-qty"> (x{quantity})</span>}
            </p>
            <p className="product-price">{formatMoney(product.priceCents)}</p>
            {product.soldOut && <p className="product-sold-out">Sold out</p>}
            {!product.soldOut && (
              <button
                type="button"
                className="product-add"
                onClick={() => onAdd(product)}
                disabled={disabled || !store.open}
                aria-label={`Add ${product.name}`}
              >
                +
              </button>
            )}
          </li>
          );
        })}
      </ul>
    </section>
  );
}
