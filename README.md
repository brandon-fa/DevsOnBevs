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

## Seeded demonstration accounts

The backend reads `config/seeded-accounts.properties` during startup and ensures there is a demonstration account for each role in `database/littleisle.sqlite`. It prints the role and email for each loaded account in the backend console.

ORD also loads `config/initial-city.properties` (restaurants, menus, courier pins, and customer delivery pins) and serves that snapshot at `GET /api/city` after login. The dashboard draws those pins on an OpenStreetMap view.

Credentials to use for the seeded accounts based on its respective role:

| Role | Email | Password |
| --- | --- | --- |
| Customer | demo.customer@littleisles.example | DemoPass123! |
| Groceries | demo.groceries@littleisles.example | DemoPass123! |
| Courier | demo.courier@littleisles.example | DemoPass123! |
| Operator | demo.operator@littleisles.example | DemoPass123! |

Note that these are only for demonstration purposes.

