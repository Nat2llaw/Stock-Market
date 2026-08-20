import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { PriceHistoryTable } from '../../../src/components/PriceHistoryTable';
import { aBar } from '../../support/fixtures';

describe('PriceHistoryTable', () => {
  const series = [
    aBar({ timestamp: '2026-08-18T13:30:00Z', open: 307.53, high: 311.49, low: 305.74, close: 310.745, volume: 34984236 }),
    aBar({ timestamp: '2026-08-17T13:30:00Z', close: 305.59, volume: 38169300 }),
  ];

  const rowHeaders = () => screen.getAllByRole('row').slice(1).map((row) => within(row).getByRole('rowheader').textContent);

  it('renders one row per stored bar in the order given, with its OHLCV figures, retrieval time and bar width', () => {
    const full = render(<PriceHistoryTable history={series} currency="USD" interval="1h" timeZone="America/New_York" />);

    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(3);
    expect(screen.getByText(/1h bars, newest first/)).toBeInTheDocument();

    const cells = within(screen.getAllByRole('row')[1]).getAllByRole('cell').map((cell) => cell.textContent ?? '');
    expect(cells[0]).toMatch(/307[.,]53/); // open
    expect(cells[1]).toMatch(/311[.,]49/); // high
    expect(cells[2]).toMatch(/305[.,]74/); // low
    expect(cells[3]).toMatch(/310[.,]7/); //  close
    expect(cells[4]).toMatch(/35\s?M/i); //  volume
    expect(cells.at(-1)).toContain('2026'); // retrieved at

    expect(rowHeaders()[0]).toContain('18 Aug');
    expect(rowHeaders()[1]).toContain('17 Aug');
    full.unmount();

    render(<PriceHistoryTable history={[aBar({ open: null, high: null, low: null, volume: null })]} currency="USD" interval="1d" timeZone="America/New_York" />);
    expect(within(screen.getAllByRole('row')[1]).getAllByText('—')).toHaveLength(4);
  });

  it('pages ten bars at a time, walking both ways and stopping at either end of the set', async () => {
    const manyBars = Array.from({ length: 23 }, (_, index) =>
      aBar({ timestamp: `2026-08-${String(23 - index).padStart(2, '0')}T13:30:00Z` }),
    );
    const { rerender } = render(<PriceHistoryTable history={manyBars} currency="USD" interval="1d" timeZone="America/New_York" />);

    expect(rowHeaders()).toHaveLength(10);
    expect(rowHeaders()[0]).toContain('23 Aug');
    expect(screen.getByText(/Showing 1–10 of 23/)).toBeInTheDocument();
    expect(screen.getByText(/Page 1 of 3/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Previous/ })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: /Next/ }));
    expect(rowHeaders()[0]).toContain('13 Aug');
    expect(screen.getByText(/Showing 11–20 of 23/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Next/ }));
    expect(rowHeaders()).toHaveLength(3);
    expect(screen.getByText(/Showing 21–23 of 23/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Next/ })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: /Previous/ }));
    expect(screen.getByText(/Showing 11–20 of 23/)).toBeInTheDocument();

    rerender(<PriceHistoryTable history={manyBars.slice(0, 12)} currency="USD" interval="1d" timeZone="America/New_York" />);
    expect(screen.getByText(/Showing 11–12 of 12/)).toBeInTheDocument();
    expect(screen.getByText(/Page 2 of 2/)).toBeInTheDocument();
  });

  it('says nothing is stored rather than showing an empty table', () => {
    render(<PriceHistoryTable history={[]} currency="USD" interval="1d" timeZone="America/New_York" />);

    expect(screen.getByText(/No historical prices stored yet/)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
