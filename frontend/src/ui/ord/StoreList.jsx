import { useEffect, useState } from "react";
import StoreMenu from "./StoreMenu.jsx";
import Cart from "./Cart.jsx";
import { placeOrder } from "./orderApi.js";

export default function StoreList() {
    const [stores, setStores] = useState([]);
    const [selectedStore, setSelectedStore] = useState(null);
    const [cart, setCart] = useState([]);
    const [message, setMessage] = useState("");

    useEffect(() => {
        fetch("/api/city", { credentials: "include" })
            .then((response) => response.json())
            .then((city) => setStores(city.restaurants || []));
    }, []);

    function chooseStore(store) {
        setSelectedStore(store);
        setCart([]);
        setMessage("");
    }

    function addToCart(item) {
        const existing = cart.find((line) => line.id === item.id);
        if (existing) {
            setCart(cart.map((line) =>
                line.id === item.id ? { ...line, quantity: line.quantity + 1 } : line
            ));
        } else {
            setCart([...cart, { id: item.id, name: item.name, quantity: 1 }]);
        }
    }

    async function submitOrder() {
        const result = await placeOrder(selectedStore.id, cart);
        if (result.ok) {
            setMessage("Order " + result.order.orderId + " placed.");
            setCart([]);
        } else {
            setMessage("Refused: " + result.error);
        }
    }

    return (
        <section>
            <h2>Stores</h2>
            <ul>
                {stores.map((store) => (
                    <li key={store.id}>
                        {store.name} ({store.status})
                        <button type="button" onClick={() => chooseStore(store)}>
                            View menu
                        </button>
                    </li>
                ))}
            </ul>

            {selectedStore && (
                <>
                    <StoreMenu store={selectedStore} onAdd={addToCart} />
                    <Cart cart={cart} onPlaceOrder={submitOrder} message={message} />
                </>
            )}
        </section>
    );
}