import { useEffect, useState } from "react";
import StoreProducts from "./StoreProducts.jsx";
import Cart from "./Cart.jsx";
import { loadStores, placeOrder } from "./orderApi.js";
import "./shopping.css";

export default function StoreList({ onFeedback, onOrderPlaced }) {
    const [stores, setStores] = useState([]);
    const [selectedStore, setSelectedStore] = useState(null);
    const [cart, setCart] = useState([]);
    const [loadError, setLoadError] = useState("");
    const [loading, setLoading] = useState(true);
    const [reload, setReload] = useState(0);
    const [destination, setDestination] = useState("");
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        const controller = new AbortController();
        async function getStores() {
            setLoading(true);
            setLoadError("");
            try {
                const loadedStores = await loadStores(controller.signal);
                if (!controller.signal.aborted) {
                    setStores(loadedStores);
                    if (reload > 0) onFeedback("Grocery stores refreshed.", "success");
                }
            } catch (error) {
                if (!controller.signal.aborted) {
                    const message = error.message || "Stores could not be loaded. Please try again.";
                    setLoadError(message);
                    onFeedback(message, "error");
                }
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        }
        getStores();
        return () => controller.abort();
    }, [reload, onFeedback]);

    function showFeedback(text, type = "error") {
        onFeedback(text, type);
    }

    function chooseStore(store) {
        setSelectedStore(store);
        setCart([]);
        let message = "Viewing products for " + store.name + ".";
        if (!store.open) message += " This store is closed.";
        if (cart.length > 0) message += " Your cart has been cleared.";
        showFeedback(message, "info");
    }

    function addToCart(product) {
        if (submitting) return;
        if (!selectedStore) {
            showFeedback("Choose a grocery store before adding products.");
            return;
        }
        if (!selectedStore.open) {
            showFeedback(`${selectedStore.name} is closed. Choose an open grocery store to add products.`);
            return;
        }
        if (product.soldOut) {
            showFeedback(`${product.name} is sold out and cannot be added to your cart. Choose another product.`);
            return;
        }
        setCart((previous) => {
            const updatedCart = [...previous];
            for (let index = 0; index < updatedCart.length; index++) {
                const line = updatedCart[index];
                if (line.id === product.id) {
                    updatedCart[index] = { id: line.id, name: line.name, quantity: line.quantity + 1 };
                    return updatedCart;
                }
            }
            updatedCart.push({ id: product.id, name: product.name, quantity: 1 });
            return updatedCart;
        });
        showFeedback(`Added ${product.name} to your cart.`, "success");
    }

    async function submitOrder() {
        if (submitting) return;
        if (!selectedStore) {
            showFeedback("Choose a grocery store before placing an order.");
            return;
        }
        if (!selectedStore.open) {
            showFeedback(`${selectedStore.name} is closed. Choose an open grocery store to place your order.`);
            return;
        }
        if (cart.length === 0) {
            showFeedback("Your cart is empty. Add a product before placing your order.");
            return;
        }
        if (!destination.trim()) {
            showFeedback("Enter a delivery address before placing your order.");
            return;
        }
        setSubmitting(true);
        try {
            const result = await placeOrder(selectedStore.id, cart, destination);
            if (result.ok) {
                showFeedback("Order " + result.order.orderId + " placed.", "success");
                setCart([]);
                onOrderPlaced(result.order);
            } else {
                showFeedback(result.error || "Your order could not be placed. Please try again.");
            }
        } catch {
            showFeedback("Your order could not be confirmed. Check your connection before trying again.");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <section className="customer-shopping">
            <h2>Stores</h2>
            {loading && <p role="status">Loading stores…</p>}
            {loadError && <button type="button" onClick={() => setReload((value) => value + 1)}>Retry loading stores</button>}
            {!loading && !loadError && stores.length === 0 && <p>No grocery stores are available right now.</p>}
            <ul>
                {stores.map((store) => (
                    <li key={store.id}>
                        {store.name} ({store.open ? "open" : "closed"})
                        <button type="button" onClick={() => chooseStore(store)} disabled={submitting}>
                            View products
                        </button>
                    </li>
                ))}
            </ul>

            {selectedStore && (
                <>
                    <StoreProducts store={selectedStore} onAdd={addToCart} disabled={submitting} />
                    <Cart cart={cart} onPlaceOrder={submitOrder}
                        destination={destination} onDestinationChange={setDestination} submitting={submitting} />
                </>
            )}
        </section>
    );
}
