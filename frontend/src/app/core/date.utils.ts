export function todayLocalIsoDate(): string {
  const now = new Date();
  const timezoneOffsetMs = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - timezoneOffsetMs).toISOString().slice(0, 10);
}

export function nextBookableIsoDate(from = new Date()): string {
  const candidate = new Date(from);
  candidate.setHours(0, 0, 0, 0);
  candidate.setDate(candidate.getDate() + 1);

  while (isWeekendDate(candidate)) {
    candidate.setDate(candidate.getDate() + 1);
  }

  return toLocalIsoDate(candidate);
}

export function isWeekendIsoDate(value: string): boolean {
  const parsed = parseIsoDate(value);
  return parsed ? isWeekendDate(parsed) : false;
}

export function toLocalIsoDate(date: Date): string {
  const timezoneOffsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - timezoneOffsetMs).toISOString().slice(0, 10);
}

function parseIsoDate(value: string): Date | null {
  if (!value) {
    return null;
  }

  const parsed = new Date(`${value}T00:00:00`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function isWeekendDate(date: Date): boolean {
  const day = date.getDay();
  return day === 0 || day === 6;
}
