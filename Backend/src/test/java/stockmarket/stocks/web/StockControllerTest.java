package stockmarket.stocks.web;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import stockmarket.stocks.config.YahooFinanceProperties;
import stockmarket.stocks.domain.PriceBar;
import stockmarket.stocks.domain.StockQuote;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.error.InvalidStockRequestException;
import stockmarket.stocks.error.StockDataUnavailableException;
import stockmarket.stocks.error.SymbolNotFoundException;
import stockmarket.stocks.persistence.entity.StockPriceBarEntity;
import stockmarket.stocks.persistence.entity.StockQuoteEntity;
import stockmarket.stocks.service.StockRetrievalService;
import stockmarket.stocks.service.StockStorageService;

@WebMvcTest(StockController.class)
@Import(StockExceptionHandler.class)
class StockControllerTest {

	private static final Instant RETRIEVED = Instant.parse("2026-08-18T12:00:00Z");
	private static final Instant SESSION = Instant.parse("2026-08-17T13:30:00Z");

	@TestConfiguration
	static class Properties {

		@Bean
		YahooFinanceProperties yahooFinanceProperties() {
			return new YahooFinanceProperties("http://localhost", Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
					Duration.ofMillis(1), Duration.ofMillis(1), 2.0, "AAPL", "1mo", "1d", "test-agent");
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StockStorageService storageService;

	@MockitoBean
	private StockRetrievalService retrievalService;

	private static StockQuoteEntity quoteEntity() {
		return quoteEntity("310.44");
	}

	private static StockQuoteEntity quoteEntity(String price) {
		return new StockQuoteEntity("AAPL", "Apple Inc.", "USD", "NasdaqGS", "America/New_York",
				new BigDecimal(price),
				new BigDecimal("333.74"), new BigDecimal("311.49"), new BigDecimal("305.74"), 30714033L,
				Instant.parse("2026-08-18T11:59:52Z"), RETRIEVED);
	}

	private static StockPriceBarEntity barEntity() {
		return new StockPriceBarEntity("AAPL", SESSION, "1d", new BigDecimal("333.51"), new BigDecimal("333.71"),
				new BigDecimal("323.68"), new BigDecimal("326.59"), 53468000L, RETRIEVED);
	}

	@Test
	@DisplayName("the overview returns the price, the history and the retrieval time, under a discoverable default ticker")
	void returnsOverview() throws Exception {
		given(storageService.latestQuote("AAPL")).willReturn(Optional.of(quoteEntity()));
		given(storageService.history("AAPL", "1d")).willReturn(List.of(barEntity()));

		mockMvc.perform(get("/stocks/default"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.symbol").value("AAPL"));

		mockMvc.perform(get("/stocks/AAPL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.symbol").value("AAPL"))
				.andExpect(jsonPath("$.interval").value("1d"))
				.andExpect(jsonPath("$.quote.price").value("310.4400"))
				.andExpect(jsonPath("$.quote.companyName").value("Apple Inc."))
				.andExpect(jsonPath("$.quote.retrievedAt").value("2026-08-18T12:00:00Z"))
				.andExpect(jsonPath("$.history[0].close").value("326.5900"))
				.andExpect(jsonPath("$.history[0].volume").value(53468000));
	}

	@Test
	@DisplayName("a lower-case symbol is one resource with its upper-case form, and money is an exact decimal string")
	void normalisesTheSymbolAndSerialisesMoneyExactly() throws Exception {
		given(storageService.latestQuote("AAPL")).willReturn(Optional.of(quoteEntity("310.74505")));
		given(storageService.history("AAPL", "1d")).willReturn(List.of());

		mockMvc.perform(get("/stocks/aapl"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.symbol").value("AAPL"))
				.andExpect(jsonPath("$.quote.price").value("310.7451"))
				.andExpect(jsonPath("$.quote.price").isString())
				.andExpect(jsonPath("$.quote.previousClose").isString())
				.andExpect(jsonPath("$.quote.volume").isNumber())
				.andExpect(jsonPath("$.quote.marketTime").value("2026-08-18T11:59:52Z"));
	}

	@Test
	@DisplayName("a symbol with nothing stored yet returns 404 with a hint, not an empty quote")
	void reportsNoStoredData() throws Exception {
		given(storageService.latestQuote("MSFT")).willReturn(Optional.empty());

		mockMvc.perform(get("/stocks/MSFT"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("No data collected yet"))
				.andExpect(jsonPath("$.symbol").value("MSFT"))
				.andExpect(jsonPath("$.hint").exists());
	}

	@Test
	@DisplayName("refreshing retrieves from the upstream, stores it, and returns the result")
	void refreshStoresAndReturns() throws Exception {
		StockSnapshot snapshot = new StockSnapshot(
				new StockQuote("AAPL", "Apple Inc.", "USD", "NasdaqGS", "America/New_York",
						new BigDecimal("310.44"), null, null, null,
						null, null, RETRIEVED),
				List.of(new PriceBar(SESSION, null, null, null, new BigDecimal("326.59"), 1L)));
		given(retrievalService.fetchSnapshot("AAPL", "1mo", "1d")).willReturn(snapshot);
		given(storageService.store(any(), eq("1d"))).willReturn(quoteEntity());
		given(storageService.history("AAPL", "1d")).willReturn(List.of(barEntity()));

		mockMvc.perform(post("/stocks/AAPL/refresh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quote.price").value("310.4400"))
				.andExpect(jsonPath("$.history.length()").value(1));
	}

	@Test
	@DisplayName("a retrieval failure becomes the status the client should act on, without leaking internals")
	void mapsRetrievalFailures() throws Exception {
		given(retrievalService.fetchSnapshot(eq("ZZZZ"), anyString(), anyString()))
				.willThrow(new SymbolNotFoundException("ZZZZ"));
		given(retrievalService.fetchSnapshot(eq("AAPL"), anyString(), anyString()))
				.willThrow(new InvalidStockRequestException("AAPL", "rejected with HTTP 422"));
		given(retrievalService.fetchSnapshot(eq("MSFT"), anyString(), anyString()))
				.willThrow(new StockDataUnavailableException("MSFT", "jdbc:secret internal detail"));

		mockMvc.perform(post("/stocks/ZZZZ/refresh"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Symbol not found"))
				.andExpect(jsonPath("$.symbol").value("ZZZZ"));

		mockMvc.perform(post("/stocks/AAPL/refresh").param("interval", "bogus"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:stocks:invalid-request"))
				.andExpect(jsonPath("$.title").value("Invalid request"))
				.andExpect(jsonPath("$.symbol").value("AAPL"))
				.andExpect(jsonPath("$.detail").value(
						"The stock data provider rejected the request. Check the range and interval."));

		mockMvc.perform(post("/stocks/MSFT/refresh"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Stock data provider unavailable"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(30))
				.andExpect(jsonPath("$.detail").value("The stock data provider could not be reached. "
						+ "Any stored data remains available."));
	}

	@Test
	@DisplayName("a symbol or interval that could not be stored is rejected before anything is looked up")
	void validatesSymbolAndInterval() throws Exception {
		mockMvc.perform(post("/stocks/AAPL/refresh").param("interval", "1decade-or-so"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/stocks/AAPLAAPLAAPLAAPLA"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/stocks/AA$PL")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/stocks/AA$PL/refresh")).andExpect(status().isBadRequest());

		Mockito.verifyNoInteractions(storageService, retrievalService);

		given(storageService.latestQuote(anyString())).willReturn(Optional.of(quoteEntity()));
		given(storageService.history(anyString(), anyString())).willReturn(List.of());
		for (String symbol : List.of("BRK-B", "BRK.B", "^GSPC", "BTC-USD", "ES=F")) {
			mockMvc.perform(get("/stocks/" + symbol)).andExpect(status().isOk());
		}
	}
}
