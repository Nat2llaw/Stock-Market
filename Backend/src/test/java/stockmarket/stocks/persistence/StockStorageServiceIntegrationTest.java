package stockmarket.stocks.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import stockmarket.stocks.domain.PriceBar;
import stockmarket.stocks.domain.StockQuote;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.persistence.entity.StockPriceBarEntity;
import stockmarket.stocks.persistence.entity.StockQuoteEntity;
import stockmarket.stocks.persistence.repository.StockPriceBarRepository;
import stockmarket.stocks.persistence.repository.StockQuoteRepository;
import stockmarket.stocks.service.StockStorageService;

@SpringBootTest
class StockStorageServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String SYMBOL = "AAPL";
	private static final Instant SESSION = Instant.parse("2026-08-17T13:30:00Z");
	private static final Instant RETRIEVED = Instant.parse("2026-08-18T12:00:00Z");

	@Autowired
	private StockStorageService storageService;

	@Autowired
	private StockQuoteRepository quoteRepository;

	@Autowired
	private StockPriceBarRepository barRepository;

	@BeforeEach
	void clean() {
		barRepository.deleteAll();
		quoteRepository.deleteAll();
	}

	private static StockSnapshot snapshot(BigDecimal price, Instant retrievedAt, List<PriceBar> history) {
		return new StockSnapshot(new StockQuote(SYMBOL, "Apple Inc.", "USD", "NasdaqGS", "America/New_York", price,
				new BigDecimal("333.74"), new BigDecimal("311.49"), new BigDecimal("305.74"), 30714033L,
				Instant.parse("2026-08-18T11:59:52Z"), retrievedAt), history);
	}

	private static PriceBar bar(Instant timestamp, String close, Long volume) {
		return new PriceBar(timestamp, new BigDecimal("333.51"), new BigDecimal("333.71"), new BigDecimal("323.68"),
				new BigDecimal(close), volume);
	}

	@Test
	@DisplayName("stores each quote as a new observation rather than overwriting the last, and keeps prices exact")
	void storesQuoteHistory() {
		storageService.store(snapshot(new BigDecimal("310.44"), RETRIEVED, List.of()), "1d");
		storageService.store(snapshot(new BigDecimal("312.1000"), RETRIEVED.plusSeconds(300), List.of()), "1d");

		assertThat(quoteRepository.count()).isEqualTo(2);

		StockQuoteEntity latest = storageService.latestQuote(SYMBOL).orElseThrow();
		assertThat(latest.getSymbol()).isEqualTo(SYMBOL);
		assertThat(latest.getCompanyName()).isEqualTo("Apple Inc.");
		assertThat(latest.getCurrency()).isEqualTo("USD");
		assertThat(latest.getPrice()).isEqualByComparingTo("312.10");
		assertThat(latest.getPreviousClose()).isEqualByComparingTo("333.74");
		assertThat(latest.getVolume()).isEqualTo(30714033L);
		assertThat(latest.getMarketTime()).isEqualTo(Instant.parse("2026-08-18T11:59:52Z"));
		assertThat(latest.getRetrievedAt()).isEqualTo(RETRIEVED.plusSeconds(300));
	}

	@Test
	@DisplayName("returns bars newest first, and re-reading a session refreshes that bar rather than duplicating it")
	void storesBarsNewestFirstAndUpsertsOnIdentity() {
		storageService.store(snapshot(new BigDecimal("310.44"), RETRIEVED, List.of(
				bar(SESSION.minusSeconds(172800), "320.00", 1L),
				bar(SESSION, "326.59", 53468000L),
				bar(SESSION.minusSeconds(86400), "323.00", 3L))), "1d");

		List<StockPriceBarEntity> history = storageService.history(SYMBOL, "1d");

		assertThat(history).extracting(StockPriceBarEntity::getBarTimestamp)
				.containsExactly(SESSION, SESSION.minusSeconds(86400), SESSION.minusSeconds(172800));
		assertThat(history.getFirst().getClose()).isEqualByComparingTo("326.59");
		assertThat(history.getFirst().getVolume()).isEqualTo(53468000L);
		assertThat(history.getFirst().getBarInterval()).isEqualTo("1d");

		storageService.store(snapshot(new BigDecimal("312.10"), RETRIEVED.plusSeconds(300),
				List.of(bar(SESSION, "331.02", 61000000L))), "1d");

		List<StockPriceBarEntity> daily = storageService.history(SYMBOL, "1d");
		assertThat(daily).hasSize(3);
		assertThat(daily.getFirst().getClose()).isEqualByComparingTo("331.02");
		assertThat(daily.getFirst().getVolume()).isEqualTo(61000000L);
		assertThat(daily.getFirst().getRetrievedAt()).isEqualTo(RETRIEVED.plusSeconds(300));

		storageService.store(snapshot(new BigDecimal("312.10"), RETRIEVED, List.of(bar(SESSION, "331.02", 1L))), "1h");
		assertThat(barRepository.count()).isEqualTo(4);
		assertThat(storageService.history(SYMBOL, "1h")).hasSize(1);
	}

	@Test
	@DisplayName("stores money at the column's scale, keeping sub-cent precision through the round trip")
	void keepsDecimalPrecisionThroughTheRoundTrip() {
		storageService.store(snapshot(new BigDecimal("310.44"), RETRIEVED, List.of(
				bar(SESSION.minusSeconds(172800), "320.00", 1L),
				bar(SESSION.minusSeconds(86400), "323.00", 2L),
				bar(SESSION, "0.1000", 3L))), "1d");

		List<StockPriceBarEntity> history = storageService.history(SYMBOL, "1d");

		assertThat(history).extracting(StockPriceBarEntity::getBarTimestamp)
				.containsExactly(SESSION, SESSION.minusSeconds(86400), SESSION.minusSeconds(172800));
		assertThat(history.getFirst().getClose()).isEqualByComparingTo(new BigDecimal("0.1"));
	}
}
