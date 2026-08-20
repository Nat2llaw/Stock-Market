package stockmarket.stocks.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import stockmarket.stocks.config.YahooFinanceProperties;
import stockmarket.stocks.domain.StockQuote;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.error.InvalidStockRequestException;
import stockmarket.stocks.error.StockDataUnavailableException;
import stockmarket.stocks.error.SymbolNotFoundException;
import stockmarket.stocks.provider.StockDataProvider;

class StockRetrievalServiceTest {

	private static final String SYMBOL = "AAPL";

	private static StockRetrievalService serviceWith(StockDataProvider provider, int maxRetries) {
		RetryPolicy policy = RetryPolicy.builder()
				.maxRetries(maxRetries)
				.delay(Duration.ofMillis(1))
				.maxDelay(Duration.ofMillis(1))
				.includes(StockDataUnavailableException.class)
				.build();
		return new StockRetrievalService(provider, new RetryTemplate(policy), properties());
	}

	private static YahooFinanceProperties properties() {
		return new YahooFinanceProperties("http://localhost", Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
				Duration.ofMillis(1), Duration.ofMillis(1), 2.0, SYMBOL, "1mo", "1d", "test-agent");
	}

	private static StockSnapshot snapshot() {
		return new StockSnapshot(new StockQuote(SYMBOL, "Apple Inc.", "USD", "NasdaqGS", null, new BigDecimal("310.44"),
				null, null, null, null, null, Instant.parse("2026-08-18T12:00:00Z")), List.of());
	}

	private static final class CountingProvider implements StockDataProvider {

		private final Supplier<StockSnapshot> behaviour;
		private final String[] arguments = new String[3];
		private int calls;

		private CountingProvider(Supplier<StockSnapshot> behaviour) {
			this.behaviour = behaviour;
		}

		static CountingProvider alwaysFailing(Supplier<RuntimeException> failure) {
			return new CountingProvider(() -> {
				throw failure.get();
			});
		}

		@Override
		public StockSnapshot fetchSnapshot(String symbol, String range, String interval) {
			calls++;
			arguments[0] = symbol;
			arguments[1] = range;
			arguments[2] = interval;
			return behaviour.get();
		}

	}

	@Test
	@DisplayName("the default-symbol entry point uses the configured ticker and does not retry a success")
	void succeedsFirstTimeUsingTheConfiguredDefaults() {
		CountingProvider provider = new CountingProvider(StockRetrievalServiceTest::snapshot);

		StockSnapshot result = serviceWith(provider, 3).fetchDefaultSnapshot();

		assertThat(result.quote().symbol()).isEqualTo(SYMBOL);
		assertThat(provider.arguments).containsExactly("AAPL", "1mo", "1d");
		assertThat(provider.calls).isEqualTo(1);
	}

	@Test
	@DisplayName("retries a transient outage and returns the eventual success")
	void retriesThenSucceeds() {
		CountingProvider provider = new CountingProvider(new Supplier<>() {
			private int attempt;

			@Override
			public StockSnapshot get() {
				if (++attempt < 3) {
					throw new StockDataUnavailableException(SYMBOL, "upstream hiccup");
				}
				return snapshot();
			}
		});

		StockSnapshot result = serviceWith(provider, 3).fetchSnapshot(SYMBOL, "1mo", "1d");

		assertThat(result).isNotNull();
		assertThat(provider.calls).isEqualTo(3);
	}

	@Test
	@DisplayName("gives up after the configured number of retries, surfacing the real cause")
	void givesUpAfterMaxRetries() {
		CountingProvider provider = CountingProvider
				.alwaysFailing(() -> new StockDataUnavailableException(SYMBOL, "upstream down"));

		assertThatExceptionOfType(StockDataUnavailableException.class)
				.isThrownBy(() -> serviceWith(provider, 2).fetchSnapshot(SYMBOL, "1mo", "1d"))
				.withMessageContaining("upstream down");

		assertThat(provider.calls).isEqualTo(3);
	}

	@Test
	@DisplayName("does not retry a failure an identical request would only repeat")
	void doesNotRetryPermanentFailures() {
		CountingProvider unknownSymbol = CountingProvider.alwaysFailing(() -> new SymbolNotFoundException("ZZZZ"));

		assertThatExceptionOfType(SymbolNotFoundException.class)
				.isThrownBy(() -> serviceWith(unknownSymbol, 3).fetchSnapshot("ZZZZ", "1mo", "1d"));
		assertThat(unknownSymbol.calls).isEqualTo(1);

		CountingProvider rejected = CountingProvider
				.alwaysFailing(() -> new InvalidStockRequestException(SYMBOL, "rejected with HTTP 422"));

		assertThatExceptionOfType(InvalidStockRequestException.class)
				.isThrownBy(() -> serviceWith(rejected, 3).fetchSnapshot(SYMBOL, "1mo", "bogus"));
		assertThat(rejected.calls).isEqualTo(1);
	}
}
