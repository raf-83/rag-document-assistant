export function FormMessage({
  kind,
  children,
}: {
  kind: "error" | "success" | "info";
  children: React.ReactNode;
}) {
  const classes = {
    error: "border-red-200 bg-red-50 text-red-800",
    success: "border-emerald-200 bg-emerald-50 text-emerald-800",
    info: "border-blue-200 bg-blue-50 text-blue-800",
  };

  return (
    <div className={`rounded-xl border px-4 py-3 text-sm leading-6 ${classes[kind]}`} role={kind === "error" ? "alert" : "status"}>
      {children}
    </div>
  );
}
