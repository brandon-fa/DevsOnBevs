import { formatMoney } from "../../data/mockData.js";

export default function StoreProducts({ store, onAdd, disabled = false }) {
  return (
    <section className="store-products" aria-labelledby="store-products-title">
      <h3 id="store-products-title">{store.name}</h3>
      <p className={`store-open ${store.open ? "is-open" : "is-closed"}`}>
        {store.open ? "Open" : "Closed"}
      </p>
      <ul className="product-list">
        {store.products.map((product) => (
          <li key={product.id} className="product-row">
            <div>
              <p className="product-name">{product.name}</p>
              <p className="product-price">{formatMoney(product.priceCents)}</p>
              {product.soldOut && <p className="product-sold-out">Sold out</p>}
            </div>
            <button
              type="button"
              className="product-add"
              onClick={() => onAdd(product)}
              disabled={disabled || product.soldOut || !store.open}
            >
              Add
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
