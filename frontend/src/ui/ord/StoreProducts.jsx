export default function StoreProducts({ store, onAdd, disabled = false }) {
  return (
    <section>
      <h3>{store.name}</h3>
      <ul>
        {store.products.map((product) => (
          <li key={product.id}>
            {product.name} ({product.soldOut ? "sold out" : "available"})
            <button type="button" onClick={() => onAdd(product)} disabled={disabled}
              aria-disabled={disabled || product.soldOut || !store.open}>
              Add
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
