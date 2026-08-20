import { useCallback, useEffect, useState } from 'react';
import { ApiError, fetchDefaultSymbol, fetchOverview, refresh, type StockOverview } from './api/stocks';
import { PriceChart } from './components/PriceChart';
import { PriceHistoryTable } from './components/PriceHistoryTable';
import { QuoteCard } from './components/QuoteCard';
import { ThemeToggle } from './components/ThemeToggle';
import { ThemeProvider } from './theme/ThemeContext';

export function App() {
  const [symbol, setSymbol] = useState<string | null>(null);
  const [overview, setOverview] = useState<StockOverview | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async (ticker: string) => {
    setLoading(true);
    try {
      setOverview(await fetchOverview(ticker));
      setError(null);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError(0, 'Unexpected error', String(caught), false));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchDefaultSymbol().then((ticker) => {
      if (cancelled) return;
      setSymbol(ticker);
      void load(ticker);
    });
    return () => {
      cancelled = true;
    };
  }, [load]);

  const onRefresh = useCallback(async () => {
    if (!symbol) return;
    setRefreshing(true);
    try {
      setOverview(await refresh(symbol));
      setError(null);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError(0, 'Unexpected error', String(caught), false));
    } finally {
      setRefreshing(false);
    }
  }, [symbol]);

  return (
    <ThemeProvider>
      <div className="app">
        <header className="app-header">
          <div>
            <h1>Stock Monitor</h1>
            <p className="muted">Tracking {symbol ?? '…'} · prices served from the local database</p>
          </div>
          <div className="header-actions">
            <ThemeToggle />
          </div>
        </header>

        {error && (
          <div className={`banner ${error.noDataYet ? 'banner-info' : 'banner-error'}`} role="alert">
            <strong>{error.title}.</strong> {error.detail}
            {error.noDataYet && ' Use “Refresh now” to collect it.'}
            {overview && !error.noDataYet && ' Showing the last data that was stored.'}
          </div>
        )}

        {!overview && (
          <p>
            <button type="button" onClick={onRefresh} disabled={refreshing || !symbol}>
              {refreshing ? 'Refreshing…' : 'Refresh now'}
            </button>
          </p>
        )}

        {loading && !overview && <p className="muted">Loading…</p>}

        {overview && (
          <main className="content">
            <QuoteCard quote={overview.quote} onRefresh={onRefresh} refreshing={refreshing} />
            <PriceChart
              history={overview.history}
              currency={overview.quote.currency}
              timeZone={overview.quote.exchangeTimezone}
            />
            <PriceHistoryTable
              history={overview.history}
              currency={overview.quote.currency}
              interval={overview.interval}
              timeZone={overview.quote.exchangeTimezone}
            />
          </main>
        )}

        <footer className="app-footer muted">
          <p>
            Data from Yahoo Finance, collected by the backend on Refresh and stored in PostgreSQL. Every timestamp
            shown is when this application retrieved the value, not when you loaded the page.
          </p>
        </footer>
      </div>
    </ThemeProvider>
  );
}
