import hashlib
import sqlite3
c = sqlite3.connect("database/littleisle.sqlite")
h = hashlib.sha256(b"DemoPass123!").hexdigest()
print("expected", h)
print(c.execute("select id, email, password_hash from users where email='demo.customer@littleisles.example'").fetchone())
