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

		List<PriceBar> bars = snapshot.history();
		assertThat(bars.getLast().close())
				.as("the last bar is the session regularMarketPrice belongs to")
				.isCloseTo(snapshot.quote().price(), within(new BigDecimal("0.01")));
		assertThat(bars).isSortedAccordingTo(Comparator.comparing(PriceBar::timestamp));

		assertThat(snapshot.quote().previousClose())
				.isEqualByComparingTo(bars.get(bars.size() - 2).close());

		assertThatExceptionOfType(SymbolNotFoundException.class)
				.isThrownBy(() -> provider.fetchSnapshot("ZZZZNOTREAL", "1mo", "1d"));
	}
}
