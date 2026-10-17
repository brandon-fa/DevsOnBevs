import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import CreateAccount from "./ui/reg/CreateAccount.jsx";
import Dashboard from "./ui/dash/Dashboard.jsx";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<CreateAccount />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
