import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../../src/App';
import { anOverview, aQuote, asPayload } from '../../support/fixtures';
import { jsonResponse, problemResponse, routes } from '../../support/http';

describe('App refresh', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('fetches fresh data for the monitored symbol, locked while the request is in flight', async () => {
    let release: (value: Response) => void = () => {};
    const fetchMock = routes({
      symbol: () => new Response('MSFT', { status: 200 }),
      refresh: () =>
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);
    await screen.findByText(/Tracking MSFT/);

    await userEvent.click(screen.getByRole('button', { name: /Refresh now/ }));

    const button = screen.getByRole('button', { name: /Refresh/ });
    expect(button).toBeDisabled();
    expect(button.textContent).toMatch(/Refreshing/);
    expect(fetchMock).toHaveBeenCalledWith('/api/stocks/MSFT/refresh', expect.objectContaining({ method: 'POST' }));

    release(jsonResponse(asPayload(anOverview({ quote: aQuote({ price: 999.99 }) }))));

    await waitFor(() => expect(document.querySelector('.price')?.textContent).toMatch(/999[.,]99/));
    expect(screen.getByRole('button', { name: /Refresh/ })).toBeEnabled();
  });

  it('keeps showing the stored price when a refresh fails, and says that is what it is showing', async () => {
    vi.stubGlobal(
      'fetch',
      routes({
        refresh: () =>
          problemResponse('urn:stocks:upstream-unavailable', 'Stock data provider unavailable', 'Down', 503),
      }),
    );

    render(<App />);
    await screen.findByRole('heading', { name: 'AAPL' });

    await userEvent.click(screen.getByRole('button', { name: /Refresh now/ }));

    const banner = await screen.findByRole('alert');
    expect(banner.textContent).toMatch(/Showing the last data that was stored/);
    expect(document.querySelector('.price')?.textContent).toMatch(/310[.,]7/);
  });
});
