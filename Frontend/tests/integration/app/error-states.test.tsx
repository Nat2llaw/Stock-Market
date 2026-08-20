import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../../src/App';
import { anOverview, asPayload } from '../../support/fixtures';
import { jsonResponse, problemResponse, routes } from '../../support/http';

describe('App error states', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('offers a refresh when nothing has been collected yet, and clears the banner once it has', async () => {
    let empty = true;
    vi.stubGlobal(
      'fetch',
      routes({
        overview: () =>
          empty
            ? problemResponse('urn:stocks:no-stored-data', 'No data collected yet', 'No stored data yet', 404)
            : jsonResponse(asPayload(anOverview())),
        refresh: () => {
          empty = false;
          return jsonResponse(asPayload(anOverview()));
        },
      }),
    );

    render(<App />);

    const banner = await screen.findByRole('alert');
    expect(banner).toHaveClass('banner-info');
    expect(banner.textContent).toMatch(/Refresh now/);

    await userEvent.click(screen.getByRole('button', { name: /Refresh now/ }));

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    expect(await screen.findByRole('heading', { name: 'AAPL' })).toBeInTheDocument();
  });

  it('reports any other failure in the server’s own words, and an unreachable backend as such', async () => {
    vi.stubGlobal(
      'fetch',
      routes({
        overview: () =>
          problemResponse(
            'urn:stocks:upstream-unavailable',
            'Stock data provider unavailable',
            'The stock data provider could not be reached.',
            503,
          ),
      }),
    );

    const upstream = render(<App />);
    const banner = await screen.findByRole('alert');
    expect(banner).toHaveClass('banner-error');
    expect(banner.textContent).toMatch(/Stock data provider unavailable/);
    expect(banner.textContent).not.toMatch(/Use .Refresh now./);
    upstream.unmount();

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    render(<App />);
    expect((await screen.findByRole('alert')).textContent).toMatch(/Cannot reach the server/);
    expect(screen.queryByText(/Loading…/)).not.toBeInTheDocument();
  });
});
