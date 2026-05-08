export function apiErrorMessage(error: unknown, fallback: string): string {
  const candidate = error as {
    status?: number;
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

export function apiErrorStatus(error: unknown): number | null {
  const candidate = error as { status?: number } | null;
  return typeof candidate?.status === 'number' ? candidate.status : null;
}

export function bookingCancellationErrorMessage(
  error: unknown,
  fallback = 'Eliminazione prenotazione non riuscita.',
): string {
  const message = apiErrorMessage(error, fallback);

  if (message.includes("gia' in corso") || message.includes('già in corso')) {
    return 'La prenotazione e` gia` iniziata e non puo` piu` essere eliminata.';
  }

  if (message.includes("gia' conclusa") || message.includes('già conclusa')) {
    return 'La prenotazione e` gia` terminata e non puo` piu` essere eliminata.';
  }

  return message;
}
