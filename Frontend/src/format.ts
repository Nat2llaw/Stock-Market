export function formatMoney(value: number | null, currency: string | null): string {
  if (value === null || value === undefined) return '—';
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: currency ?? 'USD',
      minimumFractionDigits: 2,
    }).format(value);
  } catch {
    return value.toFixed(2);
  }
}

export function formatVolume(value: number | null): string {
  if (value === null || value === undefined) return '—';
  return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

export function formatDateTime(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatDate(iso: string, timeZone: string | null): string {
  const date = new Date(iso);
  try {
    return date.toLocaleDateString(undefined, { dateStyle: 'medium', timeZone: timeZone ?? 'UTC' });
  } catch {
    // An unrecognised zone name makes Intl throw rather than degrade.
    return date.toLocaleDateString(undefined, { dateStyle: 'medium', timeZone: 'UTC' });
  }
}

export function formatRelative(iso: string, now: number = Date.now()): string {
  const seconds = Math.round((now - new Date(iso).getTime()) / 1000);
  if (!Number.isFinite(seconds)) return '';
  if (seconds < 60) return 'just now';

  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['minute', 60],
    ['hour', 3600],
    ['day', 86400],
  ];
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  let chosen: [Intl.RelativeTimeFormatUnit, number] = units[0];
  for (const unit of units) {
    if (seconds >= unit[1]) chosen = unit;
  }
  return formatter.format(-Math.round(seconds / chosen[1]), chosen[0]);
}

export function changeAgainst(price: number, previousClose: number | null): { absolute: number; percent: number } | null {
  if (previousClose === null || previousClose === undefined || previousClose === 0) return null;
  const absolute = price - previousClose;
  return { absolute, percent: (absolute / previousClose) * 100 };
}
