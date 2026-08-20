import { render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../../src/App';
import { anOverview, asPayload } from '../../support/fixtures';
import { jsonResponse, routes } from '../../support/http';

describe('App on first load', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows a loading note until the response arrives, then the price, chart and history it carried', async () => {
    let release: (value: Response) => void = () => {};
    vi.stubGlobal(
      'fetch',
      routes({
        overview: () =>
          new Promise<Response>((resolve) => {
            release = resolve;
          }),
      }),
    );

    render(<App />);
    expect(await screen.findByText(/Loading…/)).toBeInTheDocument();

    release(jsonResponse(asPayload(anOverview())));

    expect(await screen.findByRole('heading', { name: 'AAPL' })).toBeInTheDocument();
    expect(screen.queryByText(/Loading…/)).not.toBeInTheDocument();
    expect(document.querySelector('.price')?.textContent).toMatch(/310[.,]7/);
    expect(screen.getByRole('img')).toHaveAccessibleName(/Closing price from/);
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(4);
  });

  it('takes the ticker from the backend, trimmed, and falls back to AAPL when it names none', async () => {
    const fetchMock = routes({ symbol: () => new Response('MSFT\n', { status: 200 }) });
    vi.stubGlobal('fetch', fetchMock);

    const named = render(<App />);
    expect(await screen.findByText(/Tracking MSFT/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/stocks/MSFT', expect.anything());
    named.unmount();

    vi.stubGlobal('fetch', routes({ symbol: () => new Response('   ', { status: 200 }) }));

    render(<App />);
    expect(await screen.findByText(/Tracking AAPL/)).toBeInTheDocument();
  });
});
