import { ROLES } from "./accountFactory.js";

const STORAGE_KEY = "littleisle.account";

export function saveAccount(account) {
  const stored = {
    fullName: account.fullName,
    email: account.email,
    phone: account.phone,
    role: account.role,
  };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
}

export function loadAccount() {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;

  try {
    const account = JSON.parse(raw);
    if (!account || !ROLES.includes(account.role)) return null;
    if (!account.fullName || !account.email || !account.phone) return null;
    return account;
  } catch {
    return null;
  }
}

export function clearAccount() {
  localStorage.removeItem(STORAGE_KEY);
}
