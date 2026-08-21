import type { PriceBar } from '../api/stocks';
import { formatDate, formatMoney } from '../format';

interface Props {
  history: PriceBar[];
  currency: string | null;
  timeZone: string | null;
}

const WIDTH = 720;
const HEIGHT = 220;
const PADDING = { top: 16, right: 16, bottom: 28, left: 56 };

export function PriceChart({ history, currency, timeZone }: Props) {
  if (history.length < 2) {
    return (
      <section className="card" aria-labelledby="chart-heading">
        <h2 id="chart-heading">Price history</h2>
        <p className="muted">Not enough stored data to draw a chart yet.</p>
      </section>
    );
  }

  const bars = [...history].reverse();
  const closes = bars.map((bar) => bar.close);
  const min = Math.min(...closes);
  const max = Math.max(...closes);
  const span = max - min || Math.abs(max) || 1;

  const plotWidth = WIDTH - PADDING.left - PADDING.right;
  const plotHeight = HEIGHT - PADDING.top - PADDING.bottom;

  const x = (index: number) => PADDING.left + (index / (bars.length - 1)) * plotWidth;
  const y = (close: number) => PADDING.top + plotHeight - ((close - min) / span) * plotHeight;

  const line = bars.map((bar, index) => `${index === 0 ? 'M' : 'L'} ${x(index).toFixed(2)} ${y(bar.close).toFixed(2)}`).join(' ');
  const area = `${line} L ${x(bars.length - 1).toFixed(2)} ${PADDING.top + plotHeight} L ${x(0).toFixed(2)} ${
    PADDING.top + plotHeight
  } Z`;

  const rising = bars[bars.length - 1].close >= bars[0].close;
  const stroke = rising ? 'var(--up)' : 'var(--down)';

  return (
    <section className="card" aria-labelledby="chart-heading">
      <h2 id="chart-heading">Price history</h2>
      <svg
        className="chart"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label={`Closing price from ${formatDate(bars[0].timestamp, timeZone)} to ${formatDate(
          bars[bars.length - 1].timestamp,
          timeZone,
        )}, ranging from ${min.toFixed(2)} to ${max.toFixed(2)}`}
      >
        <line x1={PADDING.left} y1={PADDING.top} x2={PADDING.left} y2={PADDING.top + plotHeight} className="axis" />
        <line
          x1={PADDING.left}
          y1={PADDING.top + plotHeight}
          x2={PADDING.left + plotWidth}
          y2={PADDING.top + plotHeight}
          className="axis"
        />

        <text x={PADDING.left - 8} y={PADDING.top + 4} className="tick" textAnchor="end">
          {max.toFixed(2)}
        </text>
        <text x={PADDING.left - 8} y={PADDING.top + plotHeight} className="tick" textAnchor="end">
          {min.toFixed(2)}
        </text>

        <text x={PADDING.left} y={HEIGHT - 8} className="tick">
          {formatDate(bars[0].timestamp, timeZone)}
        </text>
        <text x={PADDING.left + plotWidth} y={HEIGHT - 8} className="tick" textAnchor="end">
          {formatDate(bars[bars.length - 1].timestamp, timeZone)}
        </text>

        <path d={area} fill={stroke} opacity="0.12" />
        <path d={line} fill="none" stroke={stroke} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
      </svg>
      <p className="muted">
        {bars.length} sessions · low {formatMoney(min, currency)} · high {formatMoney(max, currency)}
      </p>
    </section>
  );
}
