import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, DEFAULT_SYMBOL, fetchDefaultSymbol, fetchOverview, refresh } from '../../../src/api/stocks';
import { aBar, anOverview, aQuote, asPayload } from '../../support/fixtures';
import { jsonResponse } from '../../support/http';

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const failure = async (): Promise<ApiError> => (await fetchOverview('AAPL').catch((caught) => caught)) as ApiError;

describe('fetchOverview', () => {
  it('asks the /api prefix for JSON, encoding the symbol so an odd ticker cannot alter the path', async () => {
    fetchMock.mockImplementation(async () => jsonResponse(asPayload(anOverview())));

    const overview = await fetchOverview('AAPL');

    expect(fetchMock).toHaveBeenCalledWith('/api/stocks/AAPL', expect.anything());
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ headers: { Accept: 'application/json' } });
    expect(overview.symbol).toBe('AAPL');
    expect(overview.history).toHaveLength(3);

    await fetchOverview('BRK/B');
    expect(fetchMock).toHaveBeenCalledWith('/api/stocks/BRK%2FB', expect.anything());
  });

  it('turns the decimal strings the API sends into numbers, keeping every stored digit and every null', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        asPayload(
          anOverview({
            quote: aQuote({ price: 310.745, previousClose: null }),
            history: [aBar({ close: 310.745, open: null })],
          }),
        ),
      ),
    );

    const overview = await fetchOverview('AAPL');

    expect(overview.quote.price).toBe(310.745);
    expect(overview.history[0].close).toBe(310.745);
    expect(overview.quote.previousClose).toBeNull();
    expect(overview.history[0].open).toBeNull();
  });

  it('turns every kind of failure into an ApiError, flagging only "nothing collected yet" as such', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ type: 'urn:stocks:no-stored-data', title: 'No data collected yet', detail: 'x' }, 404),
    );
    const empty = await failure();
    expect(empty).toBeInstanceOf(ApiError);
    expect(empty.status).toBe(404);
    expect(empty.noDataYet).toBe(true);

    fetchMock.mockResolvedValue(
      jsonResponse(
        {
          type: 'urn:stocks:upstream-unavailable',
          title: 'Stock data provider unavailable',
          detail: 'The stock data provider could not be reached. Any stored data remains available.',
        },
        503,
      ),
    );
    const upstream = await failure();
    expect(upstream.title).toBe('Stock data provider unavailable');
    expect(upstream.detail).toContain('stored data remains available');
    expect(upstream.noDataYet).toBe(false);

    fetchMock.mockResolvedValue(new Response('<html>502 Bad Gateway</html>', { status: 502 }));
    const gateway = await failure();
    expect(gateway.status).toBe(502);
    expect(gateway.detail).toContain('502');

    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    const unreachable = await failure();
    expect(unreachable.status).toBe(0);
    expect(unreachable.title).toBe('Cannot reach the server');
  });
});

describe('refresh', () => {
  it('POSTs to the refresh endpoint and returns the freshly stored data', async () => {
    fetchMock.mockResolvedValue(jsonResponse(asPayload(anOverview())));

    const overview = await refresh('AAPL');

    expect(fetchMock).toHaveBeenCalledWith('/api/stocks/AAPL/refresh', expect.objectContaining({ method: 'POST' }));
    expect(overview.quote.price).toBe(310.745);
  });
});

describe('fetchDefaultSymbol', () => {
  it('reads the monitored ticker from JSON, and falls back to AAPL rather than leaving the page symbolless', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ symbol: 'MSFT' }));
    expect(await fetchDefaultSymbol()).toBe('MSFT');
    expect(fetchMock).toHaveBeenCalledWith('/api/stocks/default', expect.anything());
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ headers: { Accept: 'application/json' } });

    fetchMock.mockResolvedValue(jsonResponse({ symbol: '   ' }));
    expect(await fetchDefaultSymbol()).toBe(DEFAULT_SYMBOL);

    fetchMock.mockResolvedValue(jsonResponse({ symbol: null }));
    expect(await fetchDefaultSymbol()).toBe(DEFAULT_SYMBOL);

    fetchMock.mockResolvedValue(new Response('', { status: 500 }));
    expect(await fetchDefaultSymbol()).toBe(DEFAULT_SYMBOL);

    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    expect(await fetchDefaultSymbol()).toBe(DEFAULT_SYMBOL);
  });
});
