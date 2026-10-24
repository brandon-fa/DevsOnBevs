export default function Cart({ cart, onPlaceOrder, destination, onDestinationChange, submitting }) {
  return (
    <section aria-busy={submitting}>
      <h3>Your cart</h3>
      {cart.length === 0 && <p>Your cart is empty.</p>}
      <ul>
        {cart.map((line) => (
          <li key={line.id}>
            {line.name} × {line.quantity}
          </li>
        ))}
      </ul>
      <label htmlFor="delivery-address">Delivery address</label>
      <input id="delivery-address" name="deliveryAddress" type="text" autoComplete="street-address"
        required value={destination} onChange={(event) => onDestinationChange(event.target.value)} disabled={submitting} />
      <button type="button" onClick={onPlaceOrder} disabled={submitting}>
        {submitting ? "Placing order…" : "Place order"}
      </button>
    </section>
  );
}
