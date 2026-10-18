export const ROLES = Object.freeze([
  "customer",
  "groceries",
  "courier",
  "operator",
]);

export const ROLE_LABELS = Object.freeze({
  customer: "Customer",
  groceries: "Groceries",
  courier: "Courier",
  operator: "Operator",
});

export function dashboardPathForRole(role) {
  return ROLES.includes(role) ? `/dashboard/${role}` : "/login";
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function accountIssues(draft) {
  const issues = {};
  const fullName = draft.fullName?.trim() ?? "";
  const email = draft.email?.trim() ?? "";
  const phone = draft.phone?.trim() ?? "";
  const password = draft.password ?? "";

  if (!fullName) issues.fullName = "Enter your full name.";
  if (!EMAIL.test(email)) issues.email = "Enter a valid email address.";
  if (!password) issues.password = "Enter a password.";
  if (!phone) issues.phone = "Enter a phone number.";
  return issues;
}

function cleaned(draft) {
  return {
    fullName: draft.fullName.trim(),
    email: draft.email.trim(),
    phone: draft.phone.trim(),
  };
}

class Account {
  constructor(fields) {
    this.fullName = fields.fullName;
    this.email = fields.email;
    this.phone = fields.phone;
    this.role = fields.role;
    Object.freeze(this);
  }
}

class CustomerAccount extends Account {
  constructor(draft) {
    super({ ...draft, role: "customer" });
  }
}

class GroceriesAccount extends Account {
  constructor(draft) {
    super({ ...draft, role: "groceries" });
  }
}

class CourierAccount extends Account {
  constructor(draft) {
    super({ ...draft, role: "courier" });
  }
}

class OperatorAccount extends Account {
  constructor(draft) {
    super({ ...draft, role: "operator" });
  }
}

class AccountFactory {
  register(draft) {
    const issues = accountIssues(draft);
    const messages = Object.values(issues);
    if (messages.length > 0) {
      const error = new Error(messages[0]);
      error.issues = issues;
      throw error;
    }

    const account = this.createAccount(draft);
    if (!account || account.role !== this.role()) {
      throw new Error("An account must hold exactly one role.");
    }
    return account;
  }

  createAccount() {
    throw new Error("Subclasses implement the factory method.");
  }

  role() {
    throw new Error("Subclasses name the one role they create.");
  }
}

class CustomerAccountFactory extends AccountFactory {
  createAccount(draft) {
    return new CustomerAccount(cleaned(draft));
  }

  role() {
    return "customer";
  }
}

class GroceriesAccountFactory extends AccountFactory {
  createAccount(draft) {
    return new GroceriesAccount(cleaned(draft));
  }

  role() {
    return "groceries";
  }
}

class CourierAccountFactory extends AccountFactory {
  createAccount(draft) {
    return new CourierAccount(cleaned(draft));
  }

  role() {
    return "courier";
  }
}

class OperatorAccountFactory extends AccountFactory {
  createAccount(draft) {
    return new OperatorAccount(cleaned(draft));
  }

  role() {
    return "operator";
  }
}

export function factoryFor(role) {
  switch (role) {
    case "customer":
      return new CustomerAccountFactory();
    case "groceries":
      return new GroceriesAccountFactory();
    case "courier":
      return new CourierAccountFactory();
    case "operator":
      return new OperatorAccountFactory();
    default:
      throw new Error("Choose a role.");
  }
}
