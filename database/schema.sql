PRAGMA foreign_keys = ON;

CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    role TEXT NOT NULL CHECK (role IN ('customer', 'groceries', 'courier', 'operator')),
    full_name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    phone TEXT NOT NULL,
    store_code TEXT,
    courier_pin TEXT,
    courier_status TEXT,
    default_address TEXT,
    latitude REAL,
    longitude REAL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE grocery_stores (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    store_code TEXT NOT NULL UNIQUE
);

CREATE TABLE products (
    id INTEGER PRIMARY KEY,
    groceries_id INTEGER NOT NULL REFERENCES users (id),
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
    category TEXT NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0)
);

CREATE TABLE orders (
    id INTEGER PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES users (id),
    groceries_id INTEGER NOT NULL REFERENCES users (id),
    courier_id INTEGER REFERENCES users (id),
    status TEXT NOT NULL CHECK (status IN ('placed', 'accepted', 'preparing', 'ready', 'picked up', 'picked_up', 'on_the_way', 'delivered', 'cancelled')),
    delivery_address TEXT NOT NULL,
    subtotal_cents INTEGER NOT NULL CHECK (subtotal_cents >= 0),
    delivery_fee_cents INTEGER NOT NULL CHECK (delivery_fee_cents >= 0),
    service_fee_cents INTEGER NOT NULL DEFAULT 0 CHECK (service_fee_cents >= 0),
    small_order_surcharge_cents INTEGER NOT NULL DEFAULT 0 CHECK (small_order_surcharge_cents >= 0),
    total_cents INTEGER NOT NULL CHECK (total_cents = subtotal_cents + delivery_fee_cents + service_fee_cents + small_order_surcharge_cents),
    fare_version INTEGER NOT NULL DEFAULT 1 CHECK (fare_version >= 0),
    created_at TEXT NOT NULL,
    refunded INTEGER NOT NULL DEFAULT 0 CHECK (refunded IN (0, 1))
);

CREATE TABLE order_items (
    id INTEGER PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders (id),
    product_id INTEGER NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0)
);

CREATE TABLE order_transition_events (
    order_id INTEGER NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    from_state TEXT NOT NULL,
    to_state TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    reason TEXT,
    PRIMARY KEY (order_id, sequence_number)
);

CREATE TABLE ledger_entries (
    id TEXT PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders (id),
    entry_type TEXT NOT NULL CHECK (entry_type IN ('CHARGE', 'REFUND')),
    final_state TEXT NOT NULL,
    amount_cents INTEGER NOT NULL,
    recorded_at TEXT NOT NULL
);

CREATE TABLE ledger_entry_timestamps (
    ledger_entry_id TEXT NOT NULL REFERENCES ledger_entries (id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    order_state TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    PRIMARY KEY (ledger_entry_id, sequence_number)
);

CREATE UNIQUE INDEX one_charge_per_order
ON ledger_entries (order_id)
WHERE entry_type = 'CHARGE';

CREATE TRIGGER products_groceries_role
BEFORE INSERT ON products
FOR EACH ROW
WHEN (SELECT role FROM users WHERE id = NEW.groceries_id) IS NOT 'groceries'
BEGIN
    SELECT RAISE(ABORT, 'products.groceries_id must be a groceries');
END;

CREATE TRIGGER orders_party_roles
BEFORE INSERT ON orders
FOR EACH ROW
WHEN (SELECT role FROM users WHERE id = NEW.customer_id) IS NOT 'customer'
  OR (SELECT role FROM users WHERE id = NEW.groceries_id) IS NOT 'groceries'
  OR (
      NEW.courier_id IS NOT NULL
      AND (SELECT role FROM users WHERE id = NEW.courier_id) IS NOT 'courier'
  )
BEGIN
    SELECT RAISE(ABORT, 'order parties must match their roles');
END;
