import type { Quote } from '../api/stocks';
import { changeAgainst, formatDateTime, formatMoney, formatRelative, formatVolume } from '../format';

interface Props {
  quote: Quote;
  onRefresh?: () => void;
  refreshing?: boolean;
}

export function QuoteCard({ quote, onRefresh, refreshing = false }: Props) {
  const change = changeAgainst(quote.price, quote.previousClose);
  const direction = change === null ? 'flat' : change.absolute > 0 ? 'up' : change.absolute < 0 ? 'down' : 'flat';

  return (
    <section className="card quote-card" aria-labelledby="quote-heading">
      <header className="quote-header">
        <div>
          <h2 id="quote-heading">{quote.symbol}</h2>
          <p className="muted">
            {quote.companyName ?? 'Unknown company'}
            {quote.exchange ? ` · ${quote.exchange}` : ''}
          </p>
        </div>
        {onRefresh && (
          <button type="button" onClick={onRefresh} disabled={refreshing}>
            {refreshing ? 'Refreshing…' : 'Refresh now'}
          </button>
        )}
      </header>

      <p className="price">{formatMoney(quote.price, quote.currency)}</p>

      {change !== null && (
        <p className={`change change-${direction}`}>
          {change.absolute >= 0 ? '▲' : '▼'} {formatMoney(Math.abs(change.absolute), quote.currency)} (
          {change.percent.toFixed(2)}%)
          <span className="muted"> vs previous close</span>
        </p>
      )}

      <dl className="stats">
        <div>
          <dt>Previous close</dt>
          <dd>{formatMoney(quote.previousClose, quote.currency)}</dd>
        </div>
        <div>
          <dt>Day high</dt>
          <dd>{formatMoney(quote.dayHigh, quote.currency)}</dd>
        </div>
        <div>
          <dt>Day low</dt>
          <dd>{formatMoney(quote.dayLow, quote.currency)}</dd>
        </div>
        <div>
          <dt>Volume</dt>
          <dd>{formatVolume(quote.volume)}</dd>
        </div>
      </dl>

      <footer className="quote-footer muted">
        <p>
          <strong>Retrieved:</strong> {formatDateTime(quote.retrievedAt)} ({formatRelative(quote.retrievedAt)})
        </p>
        <p>
          <strong>Market time:</strong> {formatDateTime(quote.marketTime)}
        </p>
      </footer>
    </section>
  );
}
