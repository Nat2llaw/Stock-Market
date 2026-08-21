package stockmarket.stocks.provider.yahoo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import stockmarket.stocks.config.YahooFinanceProperties;
import stockmarket.stocks.domain.PriceBar;
import stockmarket.stocks.domain.StockQuote;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.error.InvalidStockRequestException;
import stockmarket.stocks.error.StockDataException;
import stockmarket.stocks.error.StockDataUnavailableException;
import stockmarket.stocks.error.SymbolNotFoundException;
import stockmarket.stocks.provider.StockDataProvider;

@Component
public class YahooFinanceStockDataProvider implements StockDataProvider {

	private static final Logger log = LoggerFactory.getLogger(YahooFinanceStockDataProvider.class);

	private static final Set<String> SESSION_INTERVALS = Set.of("1d", "5d", "1wk", "1mo", "3mo");

	private final RestClient restClient;
	private final YahooFinanceProperties properties;
	private final Clock clock;

	public YahooFinanceStockDataProvider(RestClient yahooRestClient, YahooFinanceProperties properties, Clock clock) {
		this.restClient = yahooRestClient;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public StockSnapshot fetchSnapshot(String symbol, String range, String interval) {
		String normalisedSymbol = normalise(symbol);
		String effectiveRange = blankToDefault(range, properties.defaultRange());
		String effectiveInterval = blankToDefault(interval, properties.defaultInterval());

		YahooChartResponse response = get(normalisedSymbol, effectiveRange, effectiveInterval);
		return map(normalisedSymbol, effectiveInterval, response);
	}

	private YahooChartResponse get(String symbol, String range, String interval) {
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/{symbol}")
							.queryParam("range", range)
							.queryParam("interval", interval)
							.build(symbol))
					.header(HttpHeaders.USER_AGENT, properties.userAgent())
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, response) -> {
						throw translate(symbol, response.getStatusCode());
					})
					.body(YahooChartResponse.class);
		}
		catch (StockDataException ex) {
			// Thrown by the status handler above; already the right shape.
			throw ex;
		}
		catch (ResourceAccessException ex) {
			// Connect and read timeouts, DNS failures, connection refused.
			throw new StockDataUnavailableException(symbol,
					"Could not reach Yahoo Finance for '" + symbol + "': " + ex.getMessage(), ex);
		}
		catch (RestClientException ex) {
			// Unreadable or unparseable body.
			throw new StockDataUnavailableException(symbol,
					"Could not read the Yahoo Finance response for '" + symbol + "': " + ex.getMessage(), ex);
		}
	}

	private StockDataException translate(String symbol, HttpStatusCode status) {
		if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
			return new SymbolNotFoundException(symbol);
		}

		if (status.is4xxClientError() && !status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
			return new InvalidStockRequestException(symbol,
					"Yahoo Finance rejected the request for '" + symbol + "' with HTTP " + status.value());
		}
		return new StockDataUnavailableException(symbol,
				"Yahoo Finance returned HTTP " + status.value() + " for '" + symbol + "'");
	}

	private StockSnapshot map(String symbol, String interval, YahooChartResponse response) {
		require(response != null && response.chart() != null, symbol, "an empty response");

		YahooChartResponse.ChartError error = response.chart().error();
		if (error != null) {
			if ("Not Found".equalsIgnoreCase(error.code())) {
				throw new SymbolNotFoundException(symbol);
			}
			throw new StockDataUnavailableException(symbol, "Yahoo Finance reported an error for '"
					+ symbol + "': " + error.code() + " - " + error.description());
		}

		List<YahooChartResponse.Result> results = response.chart().result();
		require(results != null && !results.isEmpty() && results.getFirst() != null, symbol, "no result");

		YahooChartResponse.Result result = results.getFirst();
		require(result.meta() != null, symbol, "a result without metadata");

		List<PriceBar> history = mapHistory(result);
		return new StockSnapshot(mapQuote(symbol, result.meta(), history, interval), history);
	}

	/** The upstream payload is untrusted; every shape we depend on is asserted before it is read. */
	private static void require(boolean condition, String symbol, String whatWasWrong) {
		if (!condition) {
			throw new StockDataUnavailableException(symbol,
					"Yahoo Finance returned " + whatWasWrong + " for '" + symbol + "'");
		}
	}

	private StockQuote mapQuote(String symbol, YahooChartResponse.Meta meta, List<PriceBar> history,
			String interval) {
		require(meta.regularMarketPrice() != null, symbol, "no current price");

		return new StockQuote(
				meta.symbol() != null ? meta.symbol() : symbol,
				meta.longName() != null ? meta.longName() : meta.shortName(),
				meta.currency(),
				meta.fullExchangeName() != null ? meta.fullExchangeName() : meta.exchangeName(),
				meta.exchangeTimezoneName(),
				meta.regularMarketPrice(),
				previousClose(history, interval),
				meta.regularMarketDayHigh(),
				meta.regularMarketDayLow(),
				meta.regularMarketVolume(),
				toInstant(meta.regularMarketTime()),
				clock.instant());
	}

	private static BigDecimal previousClose(List<PriceBar> history, String interval) {
		if (history.size() < 2 || !SESSION_INTERVALS.contains(interval.toLowerCase(Locale.ROOT))) {
			return null;
		}
		
		List<PriceBar> ordered = new ArrayList<>(history);
		ordered.sort(Comparator.comparing(PriceBar::timestamp));
		return ordered.get(ordered.size() - 2).close();
	}

	private List<PriceBar> mapHistory(YahooChartResponse.Result result) {
		List<Long> timestamps = result.timestamp();
		if (timestamps == null || timestamps.isEmpty()) {
			return List.of();
		}

		YahooChartResponse.Quote quote = quoteOf(result);
		if (quote == null) {
			return List.of();
		}

		List<PriceBar> bars = new ArrayList<>(timestamps.size());
		int skipped = 0;
		for (int i = 0; i < timestamps.size(); i++) {
			Long epochSeconds = timestamps.get(i);
			BigDecimal close = at(quote.close(), i);
			if (epochSeconds == null || close == null) {
				skipped++;
				continue;
			}
			bars.add(new PriceBar(
					Instant.ofEpochSecond(epochSeconds),
					at(quote.open(), i),
					at(quote.high(), i),
					at(quote.low(), i),
					close,
					at(quote.volume(), i)));
		}

		if (skipped > 0) {
			log.debug("Skipped {} of {} bars with no close price", skipped, timestamps.size());
		}
		return bars;
	}

	private YahooChartResponse.Quote quoteOf(YahooChartResponse.Result result) {
		if (result.indicators() == null || result.indicators().quote() == null
				|| result.indicators().quote().isEmpty()) {
			return null;
		}
		return result.indicators().quote().getFirst();
	}

	private static <T> T at(List<T> values, int index) {
		return values == null || index >= values.size() ? null : values.get(index);
	}

	private static Instant toInstant(Long epochSeconds) {
		return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
	}

	private String normalise(String symbol) {
		if (symbol == null || symbol.isBlank()) {
			return properties.defaultSymbol();
		}
		return symbol.strip().toUpperCase(Locale.ROOT);
	}

	private static String blankToDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
