package stockmarket.stocks.retrieval;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import stockmarket.stocks.config.YahooFinanceProperties;
import stockmarket.stocks.domain.PriceBar;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.error.SymbolNotFoundException;
import stockmarket.stocks.provider.yahoo.YahooFinanceStockDataProvider;

@Tag("live")
class YahooFinanceLiveIntegrationTest {

	private static final YahooFinanceProperties PROPERTIES = new YahooFinanceProperties(
			"https://query1.finance.yahoo.com/v8/finance/chart", Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
			Duration.ofMillis(500), Duration.ofSeconds(5), 2.0, "AAPL", "1mo", "1d",
			"Mozilla/5.0 (compatible; OaklandStockMarket/1.0)");

	private final YahooFinanceStockDataProvider provider = new YahooFinanceStockDataProvider(
			RestClient.builder().baseUrl(PROPERTIES.baseUrl()).build(), PROPERTIES, Clock.systemUTC());

	@Test
	@DisplayName("the live v8 chart endpoint still serves AAPL, and still reports an unknown ticker as such")
	void theUpstreamContractStillHolds() {
		StockSnapshot snapshot = provider.fetchSnapshot("AAPL", "1mo", "1d");

		assertThat(snapshot.quote().symbol()).isEqualTo("AAPL");
		assertThat(snapshot.quote().price()).isPositive();
		assertThat(snapshot.quote().currency()).isEqualTo("USD");
		assertThat(snapshot.quote().retrievedAt()).isNotNull();
		assertThat(snapshot.history()).isNotEmpty();
		assertThat(snapshot.history()).allSatisfy(bar -> {
			assertThat(bar.close()).isPositive();
			assertThat(bar.timestamp()).isNotNull();
		});

		// The quote's previous close is derived from the bars, and that derivation rests on one
		// property of the upstream: the final bar is the session the current price belongs to —
		// still moving while the market is open, settled once it shuts. Assert it against the
		// real endpoint, because if it ever stops holding, the previous close silently becomes
		// the wrong session and nothing else in the suite would notice.
		// To the cent, not bit-for-bit: Yahoo rounds regularMarketPrice to cents while the bar
		// closes arrive as raw float64 (316.83 against 316.8299865722656). Agreeing to the cent
		// is what identifies the session; demanding more would fail on the noise alone.
		List<PriceBar> bars = snapshot.history();
		assertThat(bars.getLast().close())
				.as("the last bar is the session regularMarketPrice belongs to")
				.isCloseTo(snapshot.quote().price(), within(new BigDecimal("0.01")));
		assertThat(bars).isSortedAccordingTo(Comparator.comparing(PriceBar::timestamp));

		// And so the previous close is the session before it — never Yahoo's chartPreviousClose,
		// which is the close before the requested range starts.
		assertThat(snapshot.quote().previousClose())
				.isEqualByComparingTo(bars.get(bars.size() - 2).close());

		assertThatExceptionOfType(SymbolNotFoundException.class)
				.isThrownBy(() -> provider.fetchSnapshot("ZZZZNOTREAL", "1mo", "1d"));
	}
}
