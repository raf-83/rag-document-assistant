export function LoadingState({ label = "Ładowanie..." }: { label?: string }) {
  return (
    <div className="state-box" role="status">
      <span className="spinner" aria-hidden="true" />
      <p>{label}</p>
    </div>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div className="state-box border-red-200 bg-red-50 text-red-800" role="alert">
      <div>
        <p className="font-semibold">Nie udało się pobrać danych</p>
        <p className="mt-1 text-sm">{message}</p>
      </div>
      {onRetry && (
        <button className="secondary-button" type="button" onClick={onRetry}>
          Spróbuj ponownie
        </button>
      )}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="state-box flex-col py-12 text-center">
      <div className="flex size-12 items-center justify-center rounded-2xl bg-slate-100 text-xl" aria-hidden="true">
        ◇
      </div>
      <div>
        <p className="font-semibold text-slate-900">{title}</p>
        <p className="mt-1 max-w-md text-sm text-slate-500">{description}</p>
      </div>
      {action}
    </div>
  );
}
