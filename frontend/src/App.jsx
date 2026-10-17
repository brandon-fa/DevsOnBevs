import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import CreateAccount from "./ui/reg/CreateAccount.jsx";
import Dashboard from "./ui/dash/Dashboard.jsx";
import Login from "./ui/reg/Login.jsx";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<CreateAccount />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/dashboard/:role" element={<Dashboard />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
