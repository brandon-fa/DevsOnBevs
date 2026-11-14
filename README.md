# DevsOnBevs

Little Isles - Grocery Delivery System

## Project Oberview

The **Grocery Delivery System** is a full-stack application designed to offer online grocery shopping, inventory management and real-time orders. This application emphasizes clean software architecture, object-oriented design principles, domain modelling and scalable clien-server communication.

### Key Features

* **Interactive Product Catalog:** Browse, search, and filter grocery items across multiple categories with real-time stock indicators

* **Dynamic Shopping Cart:** Add, update, or remove items with with an automated subtotal, and final order price.

* **Checkout and Order Processing:** Secure order placement with valid customer information, e.g., adress validation, delivery time-slot selection, and payment.

* **Live Order Tracking:** Real-time order status updates from grocery store preparation to doorstep delivery.

* **Inventory and Admin Management:** Backend admin access for managing store stock, pricing, and customer orders.

---

## Minimum System and OS Requirements

### Operating System Requirements

* **Windows:** Windows 10 (64-bit, version 21H2+) or Windows 11 (required for native SSMS installation).

* **macOS:** macOS 13 (Ventura) or newer.

---

## Technology Stack

* **Frontend:** React, JavaScript, HTML/CSS
* **Backend:** Java
* **Database:** Microsoft SQL Server, SQL Server Management Studio (SSMS)
* **Version Control:** Git & GitHub
* **Design & Modeling:** `[e.g., Draw.io]`
* **Team Collaboration:** `[e.g., Discord / Microsoft Teams]`

## Dependencies

* **Java Development Kit (JDK)** (v17 or v21)
* **Node.js & npm**
* **Maven** *(or Gradle)*
* **Microsoft SQL Server & SSMS**
* **Spring Boot** *(if applicable for Java REST API)*
* **Microsoft JDBC Driver** *(for Java-to-SQL Server connection)*

## 📂 Repository Structure



## How to first run frontend & backend
in terminal

frontend:
cd frontend
npm install
npm run dev

backend:
cd backend
mvn compile exec:java

## Run with Docker

Needs Docker Desktop (or Docker Engine with Compose v2) and nothing else: no Java, Maven or Node. From the repository root:

```
docker compose up --build
```

Then open http://localhost:3000. Stop with `Ctrl+C` or `docker compose down`.

- The `frontend` container serves the built React app with nginx and forwards `/api` requests to the `backend` container, like `npm run dev` does.
- The database is a copy of `database/littleisle.sqlite` kept in a Docker volume, so Docker never changes the file in git. It survives `docker compose down`. To start over from the committed file, run `docker compose down -v`.
- The backend reads `config/` from your checkout. After editing a `.properties` file, run `docker compose restart backend`. After code changes, run `docker compose up --build` again.
- To use a port other than 3000, set `WEB_PORT`. In PowerShell: `$env:WEB_PORT=8000; docker compose up --build`.

## Seeded demonstration accounts

The backend reads `config/seeded-accounts.properties` during startup and ensures there is a demonstration account for each role in `database/littleisle.sqlite`. It prints the role and email for each loaded account in the backend console.

Credentials to use for the seeded accounts based on its respective role:

| Role | Email | Password |
| --- | --- | --- |
| Customer | demo.customer@littleisles.example | DemoPass123! |
| Groceries | demo.groceries@littleisles.example | DemoPass123! |
| Courier | demo.courier@littleisles.example | DemoPass123! | 
| Operator | demo.operator@littleisles.example | DemoPass123! |

Note that these are only for demonstration purposes.

Authentication passcode for any new operator account: 111
