import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AccountPage from "./ui/dash/AccountPage.jsx";
import CompletedOrdersPage from "./ui/dash/CompletedOrdersPage.jsx";
import CreateAccount from "./ui/reg/CreateAccount.jsx";
import Dashboard from "./ui/dash/Dashboard.jsx";
import HistoryPage from "./ui/dash/HistoryPage.jsx";
import Login from "./ui/reg/Login.jsx";
import CartPage from "./ui/ord/Cart.jsx";
import TrackOrdersPage from "./ui/ord/TrackOrdersPage.jsx";
import { CartProvider } from "./ui/ord/cartContext.js";

export default function App() {
  return (
    <BrowserRouter>
      <CartProvider>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<CreateAccount />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/dashboard/:role" element={<Dashboard />} />
        <Route path="/dashboard/:role/account" element={<AccountPage />} />
        <Route path="/dashboard/:role/history" element={<HistoryPage />} />
        <Route path="/dashboard/:role/cart" element={<CartPage />} />
        <Route path="/dashboard/:role/orders" element={<TrackOrdersPage />} />
        <Route path="/dashboard/:role/completed-orders" element={<CompletedOrdersPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      </CartProvider>
    </BrowserRouter>
  );
}
