import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PriceChart } from '../../../src/components/PriceChart';
import { aBar } from '../../support/fixtures';

describe('PriceChart', () => {
  const series = [
    aBar({ timestamp: '2026-08-18T13:30:00Z', close: 310 }),
    aBar({ timestamp: '2026-08-17T13:30:00Z', close: 305 }),
    aBar({ timestamp: '2026-08-14T13:30:00Z', close: 320 }),
  ];

  const linePath = (container: HTMLElement) => container.querySelector('path[stroke]')?.getAttribute('d') ?? '';

  it('plots one point per session oldest first, scaled to the plot, and captions the range', () => {
    const { container } = render(<PriceChart history={series} currency="USD" timeZone="America/New_York" />);

    const d = linePath(container);
    expect(d).toMatch(/^M [\d.]+ [\d.]+( L [\d.]+ [\d.]+)+$/);
    expect(d.match(/[ML]/g)).toHaveLength(3);

    const ys = [...d.matchAll(/[ML] \S+ (\S+)/g)].map((match) => Number(match[1]));
    expect(Math.min(...ys)).toBeCloseTo(16, 1);
    expect(Math.max(...ys)).toBeCloseTo(192, 1);

    const labels = Array.from(container.querySelectorAll('text.tick')).map((node) => node.textContent ?? '');
    expect(labels.at(-2)).toContain('14 Aug');
    expect(labels.at(-1)).toContain('18 Aug');

    const caption = container.querySelector('p.muted')?.textContent ?? '';
    expect(caption).toMatch(/3 sessions/);
    expect(caption).toMatch(/low\s*\D*305[.,]00/);
    expect(caption).toMatch(/high\s*\D*320[.,]00/);
    expect(screen.getByRole('img')).toHaveAccessibleName(/Closing price from .* to .*, ranging from 305.00 to 320.00/);
  });

  it('says so rather than drawing a meaningless chart, and never divides by zero on a flat series', () => {
    const tooShort = render(<PriceChart history={[aBar()]} currency="USD" timeZone="America/New_York" />);
    expect(screen.getByText(/Not enough stored data/)).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    tooShort.unmount();

    const empty = render(<PriceChart history={[]} currency="USD" timeZone="America/New_York" />);
    expect(screen.getByText(/Not enough stored data/)).toBeInTheDocument();
    empty.unmount();

    const flat = render(
      <PriceChart
        history={[
          aBar({ timestamp: '2026-08-18T13:30:00Z', close: 310 }),
          aBar({ timestamp: '2026-08-17T13:30:00Z', close: 310 }),
        ]}
        currency="USD"
        timeZone="America/New_York"
      />,
    );
    expect(linePath(flat.container)).not.toContain('NaN');
  });
});
