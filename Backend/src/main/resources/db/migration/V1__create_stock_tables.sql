-- Storage for retrieved stock market data.
--
-- Two tables, because the application stores two genuinely different things:
--
--   stock_quote     — an append-only log of observations. Every successful retrieval adds a
--                     row, so "what was the price when we last looked, and when was that?"
--                     is answerable, and so is "what did we see over time?".
--
--   stock_price_bar — the historical OHLCV series. Unlike quotes these are facts about a
--                     trading session rather than about our polling, so they are keyed by the
--                     session rather than appended blindly.
--
-- Money is numeric, never double precision: binary floating point cannot represent decimal
-- fractions exactly, and prices are decimal quantities that get summed and compared.
-- Timestamps are timestamptz throughout, so an instant read from a US exchange means the same
-- thing regardless of where the application or the database is running.

create table stock_quote
(
    id             bigserial primary key,
    symbol         varchar(16)   not null,
    company_name   varchar(255),
    currency       varchar(8),
    exchange       varchar(64),
    -- IANA zone the exchange trades in ("America/New_York"). Stored because a bar's timestamp
    -- is the instant its session opened, and only this says which calendar date that was: a
    -- 13:30Z open is the 17th in New York and the 18th in Auckland. Rendering session dates in
    -- the reader's zone instead of the exchange's dates them a day out east of UTC+11.
    exchange_timezone varchar(64),
    price          numeric(19, 4) not null,
    -- The close of the session before the one `price` belongs to, derived from the retrieved
    -- bars rather than from Yahoo's `chartPreviousClose` (which is the close before the
    -- requested range starts, and so means something different for every range). Nullable:
    -- a retrieval that returned fewer than two session bars has nothing to put here, and a
    -- null reads as "no change to show" rather than as a zero move.
    previous_close numeric(19, 4),
    day_high       numeric(19, 4),
    day_low        numeric(19, 4),
    volume         bigint,
    -- When the exchange says the price was current.
    market_time    timestamptz,
    -- When this application read it. The brief requires showing users this, and it is not the
    -- same as market_time: a quote read at 02:00 may carry a market_time from the previous close.
    retrieved_at   timestamptz   not null
);

-- The dominant query is "the most recent quote for this symbol", and this index answers it
-- without a sort.
create index idx_stock_quote_symbol_retrieved_at on stock_quote (symbol, retrieved_at desc);

create table stock_price_bar
(
    id            bigserial primary key,
    symbol        varchar(16)   not null,
    -- Start of the period this bar covers.
    bar_timestamp timestamptz   not null,
    -- Bar width ("1d", "1h", ...). Part of the identity of a bar: the same instant belongs to
    -- a daily bar and to an hourly one, and they are different rows.
    bar_interval  varchar(8)    not null,
    open_price    numeric(19, 4),
    high_price    numeric(19, 4),
    low_price     numeric(19, 4),
    -- Not null: a bar with no close is not a bar, and the retrieval layer drops those rather
    -- than storing a placeholder that would corrupt any average computed over the series.
    close_price   numeric(19, 4) not null,
    volume        bigint,
    retrieved_at  timestamptz   not null,

    -- Makes repeated polling idempotent. Without it, polling every five minutes would insert a
    -- duplicate of today's bar every five minutes; with it, the write can upsert instead.
    constraint uq_stock_price_bar_symbol_interval_timestamp
        unique (symbol, bar_interval, bar_timestamp)
);

-- Serves the history endpoint, which reads one symbol newest-first over a date range.
create index idx_stock_price_bar_symbol_timestamp on stock_price_bar (symbol, bar_timestamp desc);
