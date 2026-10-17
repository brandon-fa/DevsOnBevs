PRAGMA foreign_keys = ON;

CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    role TEXT NOT NULL CHECK (role IN ('customer', 'restaurant', 'courier', 'operator')),
    full_name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    phone TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE customer_profiles (
    user_id INTEGER PRIMARY KEY REFERENCES users (id),
    delivery_address TEXT NOT NULL
);

CREATE TABLE restaurant_profiles (
    user_id INTEGER PRIMARY KEY REFERENCES users (id),
    store_name TEXT NOT NULL,
    cuisine TEXT NOT NULL,
    address TEXT NOT NULL
);

CREATE TABLE courier_profiles (
    user_id INTEGER PRIMARY KEY REFERENCES users (id),
    vehicle_type TEXT NOT NULL
);

CREATE TABLE products (
    id INTEGER PRIMARY KEY,
    restaurant_id INTEGER NOT NULL REFERENCES users (id),
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
    category TEXT NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0)
);

CREATE TABLE orders (
    id INTEGER PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES users (id),
    restaurant_id INTEGER NOT NULL REFERENCES users (id),
    courier_id INTEGER REFERENCES users (id),
    status TEXT NOT NULL CHECK (status IN ('placed', 'accepted', 'preparing', 'ready', 'picked_up', 'on_the_way', 'delivered', 'cancelled')),
    delivery_address TEXT NOT NULL,
    subtotal_cents INTEGER NOT NULL CHECK (subtotal_cents >= 0),
    delivery_fee_cents INTEGER NOT NULL CHECK (delivery_fee_cents >= 0),
    total_cents INTEGER NOT NULL CHECK (total_cents = subtotal_cents + delivery_fee_cents),
    created_at TEXT NOT NULL
);

CREATE TABLE order_items (
    id INTEGER PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders (id),
    product_id INTEGER NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0)
);

CREATE TRIGGER customer_profiles_role
BEFORE INSERT ON customer_profiles
FOR EACH ROW
WHEN (SELECT role FROM users WHERE id = NEW.user_id) IS NOT 'customer'
BEGIN
    SELECT RAISE(ABORT, 'customer_profiles.user_id must be a customer');
END;

CREATE TRIGGER restaurant_profiles_role
BEFORE INSERT ON restaurant_profiles
FOR EACH ROW
WHEN (SELECT role FROM users WHERE id = NEW.user_id) IS NOT 'restaurant'
BEGIN
    SELECT RAISE(ABORT, 'restaurant_profiles.user_id must be a restaurant');
END;

CREATE TRIGGER courier_profiles_role
BEFORE INSERT ON courier_profiles
FOR EACH ROW
WHEN (SELECT role FROM users WHERE id = NEW.user_id) IS NOT 'courier'
BEGIN
    SELECT RAISE(ABORT, 'courier_profiles.user_id must be a courier');
END;

CREATE TRIGGER products_restaurant_role
BEFORE INSERT ON products
FOR EACH ROW
WHEN (SELECT role FROM users WHERE id = NEW.restaurant_id) IS NOT 'restaurant'
BEGIN
    SELECT RAISE(ABORT, 'products.restaurant_id must be a restaurant');
END;

CREATE TRIGGER orders_party_roles
BEFORE INSERT ON orders
FOR EACH ROW
WHEN (SELECT role FROM users WHERE id = NEW.customer_id) IS NOT 'customer'
  OR (SELECT role FROM users WHERE id = NEW.restaurant_id) IS NOT 'restaurant'
  OR (
      NEW.courier_id IS NOT NULL
      AND (SELECT role FROM users WHERE id = NEW.courier_id) IS NOT 'courier'
  )
BEGIN
    SELECT RAISE(ABORT, 'order parties must match their roles');
END;
