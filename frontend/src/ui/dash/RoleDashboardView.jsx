const VIEW_LABELS = {
  restaurant: "Restaurant View",
  courier: "Courier View",
  operator: "Operator View",
};

export default function RoleDashboardView({ role }) {
  return (
    <section className="role-empty-view" aria-label={VIEW_LABELS[role]}>
      <h1>{VIEW_LABELS[role]}</h1>
    </section>
  );
}
