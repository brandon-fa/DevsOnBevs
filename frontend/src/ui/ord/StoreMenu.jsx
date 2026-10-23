export default function StoreMenu({ store, onAdd }) {
  return (
    <section>
      <h3>{store.name}</h3>
      <ul>
        {store.menu.map((item) => (
          <li key={item.id}>
            {item.name} ({item.status})
            <button type="button" onClick={() => onAdd(item)}>
              Add
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}