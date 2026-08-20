import { describe, expect, it } from 'vitest';
import { changeAgainst, formatDate, formatDateTime, formatMoney, formatRelative, formatVolume } from '../../../src/format';

describe('formatMoney', () => {
  it('rounds to cents, keeps the sign, and still prints the number when the currency is unusable', () => {
    expect(formatMoney(310.745, 'USD')).toMatch(/\$|USD/);
    expect(formatMoney(310.745, 'USD')).toMatch(/310[.,]7[45]/);
    expect(formatMoney(310.745, 'USD')).not.toMatch(/310[.,]745/);
    expect(formatMoney(-23, 'USD')).toMatch(/-|\(/);
    expect(formatMoney(310.7, null)).toMatch(/310[.,]70/);
    expect(formatMoney(310.7, 'NOT-A-CURRENCY')).toBe('310.70');
  });
});

describe('formatVolume', () => {
  it('compacts large share counts and leaves small ones legible', () => {
    expect(formatVolume(34984236)).toMatch(/^35\s?M$/i);
    expect(formatVolume(842)).toBe('842');
  });
});

describe('absent values', () => {
  it('renders a dash for anything missing, but a genuine zero for zero', () => {
    expect(formatMoney(null, 'USD')).toBe('—');
    expect(formatVolume(null)).toBe('—');
    expect(formatDateTime(null)).toBe('—');
    expect(formatMoney(0, 'USD')).toMatch(/0[.,]00/);
    expect(formatVolume(0)).toBe('0');
  });
});

describe('timestamps', () => {
  const now = Date.parse('2026-08-18T12:00:00Z');

  it('carry the year of the stored instant, and how long ago it was in the largest sensible unit', () => {
    expect(formatDateTime('2026-08-18T19:52:48Z')).toContain('2026');
    expect(formatDate('2026-08-18T13:30:00Z', 'America/New_York')).toContain('2026');
    expect(formatDate('2026-08-18T13:30:00Z', 'America/New_York')).toContain('18');

    expect(formatRelative('2026-08-18T11:59:30Z', now)).toBe('just now');
    expect(formatRelative('2026-08-18T11:55:00Z', now)).toMatch(/5 minutes ago/);
    expect(formatRelative('2026-08-18T09:00:00Z', now)).toMatch(/3 hours ago/);
    expect(formatRelative('2026-08-16T12:00:00Z', now)).toMatch(/2 days ago/);
  });
});

describe('formatDate dates a session by its exchange, not by the reader', () => {
  const SESSION_OPEN = '2026-08-17T13:30:00Z';

  it('gives the session the date the exchange had, whichever zone the reader is in', () => {
    expect(formatDate(SESSION_OPEN, 'America/New_York')).toContain('17');

    const asAViewerInAucklandWouldSeeIt = new Date(SESSION_OPEN).toLocaleDateString(undefined, {
      dateStyle: 'medium',
      timeZone: 'Pacific/Auckland',
    });
    expect(asAViewerInAucklandWouldSeeIt).toContain('18');
    expect(formatDate(SESSION_OPEN, 'America/New_York')).not.toBe(asAViewerInAucklandWouldSeeIt);

    expect(formatDate('2026-08-17T23:00:00Z', 'Australia/Sydney')).toContain('18');
  });

  it('falls back to UTC rather than to the reader when the exchange zone is unusable', () => {
    const utc = formatDate(SESSION_OPEN, 'UTC');
    expect(formatDate(SESSION_OPEN, null)).toBe(utc);
    expect(formatDate(SESSION_OPEN, 'Not/AZone')).toBe(utc);
  });
});

describe('changeAgainst', () => {
  it('computes the move in both directions, and returns null rather than dividing by zero', () => {
    const fall = changeAgainst(310.74, 333.74);
    expect(fall?.absolute).toBeCloseTo(-23, 2);
    expect(fall?.percent).toBeCloseTo(-6.89, 2);

    const rise = changeAgainst(340, 333.74);
    expect(rise?.absolute).toBeGreaterThan(0);
    expect(rise?.percent).toBeGreaterThan(0);

    expect(changeAgainst(310.74, null)).toBeNull();
    expect(changeAgainst(310.74, 0)).toBeNull();
  });
});
