import { vi } from 'vitest';
import { anOverview, asPayload } from './fixtures';

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function problemResponse(type: string, title: string, detail: string, status: number): Response {
  return jsonResponse({ type, title, detail }, status);
}

type Handler = () => Response | Promise<Response>;

export interface RouteHandlers {
  /** GET /api/stocks/default */
  symbol?: Handler;
  /** GET /api/stocks/{symbol} */
  overview?: Handler;
  /** POST /api/stocks/{symbol}/refresh */
  refresh?: Handler;
}

export function routes(handlers: RouteHandlers = {}) {
  return vi.fn(async (url: string, init?: RequestInit) => {
    if (url.endsWith('/stocks/default')) {
      return handlers.symbol?.() ?? jsonResponse({ symbol: 'AAPL' });
    }
    if (init?.method === 'POST') {
      return handlers.refresh?.() ?? jsonResponse(asPayload(anOverview()));
    }
    return handlers.overview?.() ?? jsonResponse(asPayload(anOverview()));
  });
}
