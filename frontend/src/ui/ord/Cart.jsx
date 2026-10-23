export default function Cart({ cart, onPlaceOrder, message }) {
  return (
    <section>
      <h3>Your cart</h3>
      {cart.length === 0 && <p>Your cart is empty.</p>}
      <ul>
        {cart.map((line) => (
          <li key={line.id}>
            {line.name} × {line.quantity}
          </li>
        ))}
      </ul>
      <button type="button" onClick={onPlaceOrder} disabled={cart.length === 0}>
        Place order
      </button>
      {message && <p>{message}</p>}
    </section>
  );
}