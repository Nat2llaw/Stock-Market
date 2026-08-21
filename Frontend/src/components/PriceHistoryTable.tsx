import { useState } from 'react';
import type { PriceBar } from '../api/stocks';
import { formatDate, formatDateTime, formatMoney, formatVolume } from '../format';

const PAGE_SIZE = 10;

interface Props {
  history: PriceBar[];
  currency: string | null;
  interval: string;
  timeZone: string | null;
}

export function PriceHistoryTable({ history, currency, interval, timeZone }: Props) {
  const [page, setPage] = useState(1);

  if (history.length === 0) {
    return (
      <section className="card" aria-labelledby="history-heading">
        <h2 id="history-heading">Stored history</h2>
        <p className="muted">No historical prices stored yet.</p>
      </section>
    );
  }

  const pageCount = Math.ceil(history.length / PAGE_SIZE);
  const currentPage = Math.min(page, pageCount);
  const start = (currentPage - 1) * PAGE_SIZE;
  const visible = history.slice(start, start + PAGE_SIZE);

  return (
    <section className="card" aria-labelledby="history-heading">
      <h2 id="history-heading">
        Stored history{' '}
        <span className="muted">
          ({history.length} {history.length === 1 ? 'result' : 'results'}, {interval} bars, newest first, sessions
          dated in {timeZone ?? 'UTC'})
        </span>
      </h2>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">Session</th>
              <th scope="col">Open</th>
              <th scope="col">High</th>
              <th scope="col">Low</th>
              <th scope="col">Close</th>
              <th scope="col">Volume</th>
              <th scope="col">Retrieved</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((bar) => (
              <tr key={bar.timestamp}>
                <th scope="row">{formatDate(bar.timestamp, timeZone)}</th>
                <td>{formatMoney(bar.open, currency)}</td>
                <td>{formatMoney(bar.high, currency)}</td>
                <td>{formatMoney(bar.low, currency)}</td>
                <td>{formatMoney(bar.close, currency)}</td>
                <td>{formatVolume(bar.volume)}</td>
                <td className="muted">{formatDateTime(bar.retrievedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <nav className="pagination" aria-label="Stored history pages">
        <button type="button" onClick={() => setPage(currentPage - 1)} disabled={currentPage === 1}>
          ‹ Previous
        </button>
        <p className="muted" aria-live="polite">
          Showing {start + 1}–{start + visible.length} of {history.length} · Page {currentPage} of {pageCount}
        </p>
        <button type="button" onClick={() => setPage(currentPage + 1)} disabled={currentPage === pageCount}>
          Next ›
        </button>
      </nav>
    </section>
  );
}
