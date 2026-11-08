// Enum values are the same constant names the Android app stores, so a backup moves between
// the two without translation. Order matters: it is the display and sort order.

export const STORAGE_AREAS = ['FRIDGE', 'FREEZER', 'PANTRY'];

export const CATEGORIES = [
  'VEGETABLES', 'FRUIT', 'MEAT', 'SEAFOOD', 'TOFU_SOY', 'DAIRY_EGGS', 'FROZEN_STAPLES',
  'GRAINS_NOODLES', 'SEASONING', 'SNACKS', 'BEVERAGE', 'PREPARED', 'OTHER',
];

export const FINISH_REASONS = ['USED', 'DISCARDED'];

/** Store names are proper nouns, so they are not translated. */
export const STORES = {
  HMART: 'H-Mart',
  TT: 'T&T Supermarket',
  TRADER_JOES: "Trader Joe's",
  WHOLE_FOODS: 'Whole Foods',
  OTHER: 'Other',
};

/** How far ahead the list flags food, in days. One number so header and rows agree. */
export const DUE_SOON_DAYS = 7;

// ---- calendar days ---------------------------------------------------------
// Dates are ISO "YYYY-MM-DD" strings in the phone's local time zone, never timestamps,
// so a day never slides by a time-zone offset.

export function today(now = new Date()) {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function toUtc(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
}

function fromUtc(ms) {
  return new Date(ms).toISOString().slice(0, 10);
}

export function isIsoDate(text) {
  if (typeof text !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(text)) return false;
  return fromUtc(toUtc(text)) === text;
}

export function addDays(iso, days) {
  return fromUtc(toUtc(iso) + days * 86_400_000);
}

export function daysBetween(fromIso, toIso) {
  return Math.round((toUtc(toIso) - toUtc(fromIso)) / 86_400_000);
}

/** Adds whole months, clamping to the month's last day (Jan 31 + 1 month = Feb 28). */
export function addMonths(iso, months) {
  const [y, m, d] = iso.split('-').map(Number);
  const target = new Date(Date.UTC(y, m - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0))
    .getUTCDate();
  target.setUTCDate(Math.min(d, lastDay));
  return fromUtc(target.getTime());
}

/** The Monday on or before [iso]. Weeks start on Monday, as in ISO and in China. */
export function weekStart(iso) {
  const weekday = new Date(toUtc(iso)).getUTCDay(); // 0 = Sunday
  return addDays(iso, -((weekday + 6) % 7));
}

/** Negative = already expired. Null = expiry not tracked. */
export function daysLeft(item, on = today()) {
  return item.expiresOn ? daysBetween(on, item.expiresOn) : null;
}

/** Bare counts read better than "2.0", and halves still need to show as "0.5". */
export function formatQuantity(value) {
  return Number.isInteger(value) ? String(value) : String(Math.round(value * 1000) / 1000);
}

export function formatMoney(cents) {
  return (cents / 100).toFixed(2);
}
