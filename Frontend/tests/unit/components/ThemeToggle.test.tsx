import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ThemeToggle } from '../../../src/components/ThemeToggle';
import { ThemeProvider } from '../../../src/theme/ThemeContext';

describe('ThemeToggle', () => {
  beforeEach(() => {
    window.localStorage.clear();
    delete document.documentElement.dataset.theme;
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  const renderToggle = () =>
    render(
      <ThemeProvider>
        <ThemeToggle />
      </ThemeProvider>,
    );

  it('starts light, and switches the palette the stylesheet reads, names the mode, remembers it, and switches back', async () => {
    renderToggle();

    expect(screen.getByRole('switch', { name: /Dark mode/ })).not.toBeChecked();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe('light');

    await userEvent.click(screen.getByRole('switch'));

    expect(screen.getByRole('switch')).toBeChecked();
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(screen.getByText('Dark')).toBeInTheDocument();
    expect(window.localStorage.getItem('stock-monitor-theme')).toBe('dark');

    await userEvent.click(screen.getByRole('switch'));

    expect(document.documentElement.dataset.theme).toBe('light');
    expect(screen.getByText('Light')).toBeInTheDocument();
  });

  it('opens in the stored theme rather than the default one', () => {
    window.localStorage.setItem('stock-monitor-theme', 'dark');

    renderToggle();

    expect(screen.getByRole('switch')).toBeChecked();
    expect(document.documentElement.dataset.theme).toBe('dark');
  });
});
