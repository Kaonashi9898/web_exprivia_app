export const BOOKING_DAY_START = '09:00';
export const BOOKING_DAY_END = '18:00';
export const BOOKING_TIME_OPTIONS = [
  '09:00',
  '10:00',
  '11:00',
  '12:00',
  '13:00',
  '14:00',
  '15:00',
  '16:00',
  '17:00',
  '18:00',
] as const;

export const BOOKING_START_OPTIONS = BOOKING_TIME_OPTIONS.slice(0, -1);
export const BOOKING_END_OPTIONS = BOOKING_TIME_OPTIONS.slice(1);

export function isWithinBookingWindow(startTime: string, endTime: string): boolean {
  return startTime >= BOOKING_DAY_START
    && endTime <= BOOKING_DAY_END
    && startTime < endTime;
}

export function nextBookingTimeOption(value: string): string | null {
  const index = BOOKING_TIME_OPTIONS.indexOf(value as (typeof BOOKING_TIME_OPTIONS)[number]);
  if (index < 0 || index === BOOKING_TIME_OPTIONS.length - 1) {
    return null;
  }
  return BOOKING_TIME_OPTIONS[index + 1];
}

export function ceilBookingStartOption(value: string): string | null {
  return BOOKING_START_OPTIONS.find((option) => option >= value) ?? null;
}
