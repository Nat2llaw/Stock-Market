import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { QuoteCard } from '../../../src/components/QuoteCard';
import { aQuote } from '../../support/fixtures';

describe('QuoteCard', () => {
  it('shows the ticker, company, exchange, price and the two times behind it', () => {
    const { container } = render(<QuoteCard quote={aQuote({ price: 310.74 })} />);

    expect(screen.getByRole('heading', { name: 'AAPL' })).toBeInTheDocument();
    expect(screen.getByText(/Apple Inc\./)).toBeInTheDocument();
    expect(screen.getByText(/NasdaqGS/)).toBeInTheDocument();
    expect(container.querySelector('.price')?.textContent).toMatch(/310[.,]74/);

    const retrieved = screen.getByText(/Retrieved:/).parentElement;
    expect(retrieved?.textContent).toContain('2026');
    expect(retrieved?.textContent).toMatch(/ago|just now/);
    expect(screen.getByText(/Market time:/)).toBeInTheDocument();
  });

  it('marks the move against the previous close, and drops it for dashes when there is nothing to compare', () => {
    const fall = render(<QuoteCard quote={aQuote({ price: 310.74, previousClose: 333.74 })} />);
    const down = fall.container.querySelector('.change');
    expect(down).toHaveClass('change-down');
    expect(down?.textContent).toMatch(/▼\s*\D*23[.,]00/);
    expect(down?.textContent).toMatch(/-6\.89%/);
    fall.unmount();

    const rise = render(<QuoteCard quote={aQuote({ price: 340, previousClose: 333.74 })} />);
    expect(rise.container.querySelector('.change')).toHaveClass('change-up');
    expect(rise.container.querySelector('.change')?.textContent).toContain('▲');
    rise.unmount();

    const sparse = render(
      <QuoteCard
        quote={aQuote({
          companyName: null,
          currency: null,
          exchange: null,
          previousClose: null,
          dayHigh: null,
          dayLow: null,
          volume: null,
          marketTime: null,
        })}
      />,
    );
    expect(screen.getByText(/Unknown company/)).toBeInTheDocument();
    expect(sparse.container.querySelector('.change')).toBeNull();
    expect(within(sparse.container.querySelector('.stats') as HTMLElement).getAllByText('—')).toHaveLength(4);
  });
});
