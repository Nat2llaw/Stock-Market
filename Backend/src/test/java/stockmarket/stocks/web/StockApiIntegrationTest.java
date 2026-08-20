package stockmarket.stocks.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import stockmarket.stocks.domain.PriceBar;
import stockmarket.stocks.domain.StockQuote;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.error.StockDataUnavailableException;
import stockmarket.stocks.persistence.AbstractPostgresIntegrationTest;
import stockmarket.stocks.persistence.repository.StockPriceBarRepository;
import stockmarket.stocks.persistence.repository.StockQuoteRepository;
import stockmarket.stocks.provider.StockDataProvider;

@SpringBootTest
@AutoConfigureMockMvc
class StockApiIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final Instant RETRIEVED = Instant.parse("2026-08-18T12:00:00Z");
	private static final Instant SESSION = Instant.parse("2026-08-17T13:30:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StockQuoteRepository quoteRepository;

	@Autowired
	private StockPriceBarRepository barRepository;

	@MockitoBean
	private StockDataProvider provider;

	@BeforeEach
	void clean() {
		barRepository.deleteAll();
		quoteRepository.deleteAll();
	}

	private static StockSnapshot snapshot(String price, String close) {
		return new StockSnapshot(
				new StockQuote("AAPL", "Apple Inc.", "USD", "NasdaqGS", "America/New_York", new BigDecimal(price),
						new BigDecimal("333.74"), null, null, 30714033L, Instant.parse("2026-08-18T11:59:52Z"),
						RETRIEVED),
				List.of(new PriceBar(SESSION, new BigDecimal("333.51"), new BigDecimal("333.71"),
						new BigDecimal("323.68"), new BigDecimal(close), 53468000L)));
	}

	@Test
	@DisplayName("refresh retrieves, persists and serves the data back, updating the open session in place")
	void refreshThenRead() throws Exception {
		given(provider.fetchSnapshot(anyString(), anyString(), anyString())).willReturn(snapshot("310.44", "326.59"));

		mockMvc.perform(post("/stocks/AAPL/refresh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quote.price").value("310.4400"));

		mockMvc.perform(get("/stocks/AAPL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.symbol").value("AAPL"))
				.andExpect(jsonPath("$.quote.price").value("310.4400"))
				.andExpect(jsonPath("$.quote.retrievedAt").value("2026-08-18T12:00:00Z"))
				.andExpect(jsonPath("$.quote.exchangeTimezone").value("America/New_York"))
				.andExpect(jsonPath("$.history.length()").value(1))
				.andExpect(jsonPath("$.history[0].close").value("326.5900"));

		given(provider.fetchSnapshot(anyString(), anyString(), anyString())).willReturn(snapshot("312.10", "331.02"));
		mockMvc.perform(post("/stocks/AAPL/refresh")).andExpect(status().isOk());

		mockMvc.perform(get("/stocks/AAPL"))
				.andExpect(jsonPath("$.history.length()").value(1))
				.andExpect(jsonPath("$.history[0].close").value("331.0200"))
				.andExpect(jsonPath("$.quote.price").value("312.1000"));
	}

	@Test
	@DisplayName("a symbol never polled returns 404 with a hint that carries the servlet context path")
	void unseenSymbolIsNotFound() throws Exception {
		mockMvc.perform(get("/stocks/AAPL"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("No data collected yet"));

		mockMvc.perform(get("/api/stocks/AAPL").contextPath("/api"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.hint").value("POST /api/stocks/AAPL/refresh to retrieve it now"));
	}

	@Test
	@DisplayName("stored data stays readable while the upstream is down")
	void storedDataSurvivesAnOutage() throws Exception {
		given(provider.fetchSnapshot(anyString(), anyString(), anyString())).willReturn(snapshot("310.44", "326.59"));
		mockMvc.perform(post("/stocks/AAPL/refresh")).andExpect(status().isOk());

		given(provider.fetchSnapshot(anyString(), anyString(), anyString()))
				.willThrow(new StockDataUnavailableException("AAPL", "upstream down"));

		mockMvc.perform(post("/stocks/AAPL/refresh"))
				.andExpect(status().isServiceUnavailable());

		mockMvc.perform(get("/stocks/AAPL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quote.price").value("310.4400"))
				.andExpect(jsonPath("$.quote.retrievedAt").value("2026-08-18T12:00:00Z"));
	}
}
