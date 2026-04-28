export function apiErrorMessage(error: unknown, fallback: string): string {
  const candidate = error as {
    error?: {
      errore?: string;
      message?: string;
    };
  } | null;

  return candidate?.error?.errore ?? candidate?.error?.message ?? fallback;
}
