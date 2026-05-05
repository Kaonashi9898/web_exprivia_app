export function apiErrorMessage(error: unknown, fallback: string): string {
  const candidate = error as {
    error?: {
      errore?: string;
      message?: string;
      dettagli?: Record<string, string>;
    };
  } | null;

  const details = candidate?.error?.dettagli;
  const detailMessage = details ? Object.values(details).find((value) => !!value) : null;

  return detailMessage ?? candidate?.error?.errore ?? candidate?.error?.message ?? fallback;
}
