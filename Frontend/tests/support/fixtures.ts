import type { PriceBar, Quote, StockOverview, StockOverviewPayload } from '../../src/api/stocks';

export const RETRIEVED = '2026-08-18T19:52:48.081150Z';

export function aQuote(overrides: Partial<Quote> = {}): Quote {
  return {
    symbol: 'AAPL',
    companyName: 'Apple Inc.',
    currency: 'USD',
    exchange: 'NasdaqGS',
    exchangeTimezone: 'America/New_York',
    price: 310.745,
    previousClose: 333.74,
    dayHigh: 311.49,
    dayLow: 305.74,
    volume: 34984236,
    marketTime: '2026-08-18T19:52:47Z',
    retrievedAt: RETRIEVED,
    ...overrides,
  };
}

export function aBar(overrides: Partial<PriceBar> = {}): PriceBar {
  return {
    timestamp: '2026-08-18T13:30:00Z',
    open: 307.53,
    high: 311.49,
    low: 305.74,
    close: 310.745,
    volume: 34984236,
    retrievedAt: RETRIEVED,
    ...overrides,
  };
}

export function asPayload(overview: StockOverview): StockOverviewPayload {
  return {
    ...overview,
    quote: {
      ...overview.quote,
      price: money(overview.quote.price),
      previousClose: optionalMoney(overview.quote.previousClose),
      dayHigh: optionalMoney(overview.quote.dayHigh),
      dayLow: optionalMoney(overview.quote.dayLow),
    },
    history: overview.history.map((bar) => ({
      ...bar,
      open: optionalMoney(bar.open),
      high: optionalMoney(bar.high),
      low: optionalMoney(bar.low),
      close: money(bar.close),
    })),
  };
}

function money(value: number): string {
  return value.toFixed(4);
}

function optionalMoney(value: number | null): string | null {
  return value === null ? null : value.toFixed(4);
}

export function anOverview(overrides: Partial<StockOverview> = {}): StockOverview {
  return {
    symbol: 'AAPL',
    interval: '1d',
    quote: aQuote(),
    history: [
      aBar(),
      aBar({ timestamp: '2026-08-17T13:30:00Z', close: 305.59, volume: 38169300 }),
      aBar({ timestamp: '2026-08-14T13:30:00Z', close: 305.93, volume: 28200000 }),
    ],
    ...overrides,
  };
}
