export interface Quote {
  symbol: string;
  companyName: string | null;
  currency: string | null;
  exchange: string | null;
  exchangeTimezone: string | null;
  price: number;
  previousClose: number | null;
  dayHigh: number | null;
  dayLow: number | null;
  volume: number | null;
  marketTime: string | null;
  retrievedAt: string;
}

export interface PriceBar {
  timestamp: string;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number;
  volume: number | null;
  retrievedAt: string;
}

export interface StockOverview {
  symbol: string;
  interval: string;
  quote: Quote;
  history: PriceBar[];
}

interface QuotePayload extends Omit<Quote, 'price' | 'previousClose' | 'dayHigh' | 'dayLow'> {
  price: string;
  previousClose: string | null;
  dayHigh: string | null;
  dayLow: string | null;
}

interface PriceBarPayload extends Omit<PriceBar, 'open' | 'high' | 'low' | 'close'> {
  open: string | null;
  high: string | null;
  low: string | null;
  close: string;
}

export interface StockOverviewPayload extends Omit<StockOverview, 'quote' | 'history'> {
  quote: QuotePayload;
  history: PriceBarPayload[];
}

function decimal(value: string | null): number | null {
  if (value === null || value === undefined) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function toQuote(payload: QuotePayload): Quote {
  return {
    ...payload,
    price: Number(payload.price),
    previousClose: decimal(payload.previousClose),
    dayHigh: decimal(payload.dayHigh),
    dayLow: decimal(payload.dayLow),
  };
}

function toPriceBar(payload: PriceBarPayload): PriceBar {
  return {
    ...payload,
    open: decimal(payload.open),
    high: decimal(payload.high),
    low: decimal(payload.low),
    close: Number(payload.close),
  };
}

function toOverview(payload: StockOverviewPayload): StockOverview {
  return {
    ...payload,
    quote: toQuote(payload.quote),
    history: payload.history.map(toPriceBar),
  };
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    readonly detail: string,
    readonly noDataYet: boolean,
  ) {
    super(detail || title);
  }
}

const BASE = '/api';

export const DEFAULT_SYMBOL = 'AAPL';

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${BASE}${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...init?.headers },
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server', 'The API did not respond. Is the backend running?', false);
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = await response.json();
    return new ApiError(
      response.status,
      problem.title ?? 'Request failed',
      problem.detail ?? '',
      problem.type === 'urn:stocks:no-stored-data',
    );
  } catch {
    return new ApiError(response.status, 'Request failed', `The server returned ${response.status}. Refresh to get stock data`, false);
  }
}

export async function fetchDefaultSymbol(): Promise<string> {
  try {
    const { symbol } = await requestJson<{ symbol: string | null }>('/stocks/default');
    return symbol?.trim() || DEFAULT_SYMBOL;
  } catch {
    return DEFAULT_SYMBOL;
  }
}

export async function fetchOverview(symbol: string): Promise<StockOverview> {
  return toOverview(await requestJson<StockOverviewPayload>(`/stocks/${encodeURIComponent(symbol)}`));
}

export async function refresh(symbol: string): Promise<StockOverview> {
  return toOverview(
    await requestJson<StockOverviewPayload>(`/stocks/${encodeURIComponent(symbol)}/refresh`, { method: 'POST' }),
  );
}
