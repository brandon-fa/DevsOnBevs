INSERT INTO users (id, role, full_name, email, password_hash, phone, created_at) VALUES
    (1, 'restaurant', 'Leila Mansour', 'leila@oliveandrye.example', 'sha256:placeholder', '514-555-0101', '2026-09-01 10:00:00'),
    (2, 'restaurant', 'Marcus Grant', 'marcus@islandpantry.example', 'sha256:placeholder', '514-555-0102', '2026-09-01 10:05:00'),
    (3, 'restaurant', 'Priya Shah', 'priya@spiceroute.example', 'sha256:placeholder', '514-555-0103', '2026-09-01 10:10:00'),
    (4, 'restaurant', 'Hana Khoury', 'hana@cedarbasket.example', 'sha256:placeholder', '514-555-0104', '2026-09-01 10:15:00'),
    (5, 'restaurant', 'Mei Lin', 'mei@pearlmarket.example', 'sha256:placeholder', '514-555-0105', '2026-09-01 10:20:00'),
    (6, 'courier', 'Samir Haddad', 'samir@couriers.littleisle.example', 'sha256:placeholder', '514-555-0106', '2026-09-02 09:00:00'),
    (7, 'courier', 'Nadia Pelletier', 'nadia@couriers.littleisle.example', 'sha256:placeholder', '514-555-0107', '2026-09-02 09:05:00'),
    (8, 'customer', 'Amira Benali', 'amira@customers.littleisle.example', 'sha256:placeholder', '514-555-0108', '2026-09-03 14:00:00'),
    (9, 'customer', 'Leo Chen', 'leo@customers.littleisle.example', 'sha256:placeholder', '514-555-0109', '2026-09-03 14:10:00'),
    (10, 'operator', 'Jordan Hale', 'jordan@littleisle.example', 'sha256:placeholder', '514-555-0110', '2026-09-01 08:00:00');

INSERT INTO restaurant_profiles (user_id, store_name, cuisine, address) VALUES
    (1, 'Olive & Rye', 'Mediterranean', '120 Avenue du Mont-Royal E, Montreal'),
    (2, 'Island Pantry', 'Caribbean', '5400 Avenue de Monkland, Montreal'),
    (3, 'Spice Route', 'South Asian', '800 Rue Jean-Talon Ouest, Montreal'),
    (4, 'Cedar Basket', 'Levantine', '5123 Avenue du Parc, Montreal'),
    (5, 'Pearl Market', 'East Asian', '70 Rue de la Gauchetière O, Montreal');

INSERT INTO courier_profiles (user_id, vehicle_type) VALUES
    (6, 'bicycle'),
    (7, 'car');

INSERT INTO customer_profiles (user_id, delivery_address) VALUES
    (8, '418 Avenue Duluth, Montreal'),
    (9, '88 Rue Jean-Talon Ouest, Montreal');

INSERT INTO products (id, restaurant_id, name, description, price_cents, category, stock) VALUES
    (1, 1, 'Castelvetrano olives', 'Jar of mild green olives', 650, 'Pantry', 40),
    (2, 1, 'Halloumi', 'Grilling cheese', 890, 'Dairy', 24),
    (3, 1, 'Sesame bread', 'Fresh loaf', 425, 'Bakery', 18),
    (4, 2, 'Scotch bonnet peppers', 'Fresh hot peppers', 375, 'Produce', 30),
    (5, 2, 'Green seasoning', 'Herb marinade', 540, 'Pantry', 22),
    (6, 2, 'Plantains', 'Ripe plantains', 499, 'Produce', 36),
    (7, 3, 'Atta flour', 'Stone-ground wheat flour', 699, 'Pantry', 28),
    (8, 3, 'Garam masala', 'Ground spice blend', 450, 'Spices', 40),
    (9, 3, 'Basmati rice', 'Aged basmati, 2 kg', 899, 'Pantry', 32),
    (10, 4, 'Labneh', 'Strained yogurt', 695, 'Dairy', 20),
    (11, 4, 'Pita', 'Six-pack of pita', 450, 'Bakery', 26),
    (12, 4, 'Green olives', 'Cracked green olives', 350, 'Pantry', 34),
    (13, 5, 'Jasmine rice', 'Fragrant rice, 2 kg', 799, 'Pantry', 30),
    (14, 5, 'Shaoxing wine', 'Cooking wine', 650, 'Pantry', 16),
    (15, 5, 'Baby bok choy', 'Fresh bunch', 325, 'Produce', 22);

INSERT INTO orders (
    id, customer_id, restaurant_id, courier_id, status, delivery_address,
    subtotal_cents, delivery_fee_cents, total_cents, created_at
) VALUES
    (1042, 8, 4, 7, 'on_the_way', '418 Avenue Duluth, Montreal', 1495, 399, 1894, '2026-10-16 16:10:00'),
    (1038, 8, 2, 6, 'delivered', '418 Avenue Duluth, Montreal', 1538, 399, 1937, '2026-10-12 18:20:00'),
    (1031, 9, 3, 7, 'delivered', '88 Rue Jean-Talon Ouest, Montreal', 1349, 399, 1748, '2026-10-08 12:05:00'),
    (1024, 8, 1, 6, 'delivered', '418 Avenue Duluth, Montreal', 1540, 399, 1939, '2026-10-02 17:40:00');

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price_cents) VALUES
    (1, 1042, 10, 1, 695),
    (2, 1042, 11, 1, 450),
    (3, 1042, 12, 1, 350),
    (4, 1038, 6, 2, 499),
    (5, 1038, 5, 1, 540),
    (6, 1031, 9, 1, 899),
    (7, 1031, 8, 1, 450),
    (8, 1024, 1, 1, 650),
    (9, 1024, 2, 1, 890);
