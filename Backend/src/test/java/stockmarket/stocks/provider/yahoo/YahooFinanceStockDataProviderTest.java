package stockmarket.stocks.provider.yahoo;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestClient;

import stockmarket.stocks.config.YahooFinanceProperties;
import stockmarket.stocks.domain.PriceBar;
import stockmarket.stocks.domain.StockQuote;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.error.InvalidStockRequestException;
import stockmarket.stocks.error.StockDataUnavailableException;
import stockmarket.stocks.error.SymbolNotFoundException;

class YahooFinanceStockDataProviderTest {

	private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart";

	private static final String AAPL_URI = BASE_URL + "/AAPL?range=1mo&interval=1d";

	private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

	private MockRestServiceServer server;
	private YahooFinanceStockDataProvider provider;

	@BeforeEach
	void setUp() {
		freshProvider();
	}

	private void freshProvider() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.provider = new YahooFinanceStockDataProvider(builder.build(), properties(),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static YahooFinanceProperties properties() {
		return new YahooFinanceProperties(BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
				Duration.ofMillis(500), Duration.ofSeconds(5), 2.0, "AAPL", "1mo", "1d", "test-agent");
	}

	private void expect(String uri, ResponseCreator response) {
		server.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(response);
	}

	private static String fixture(String name) throws IOException {
		return new ClassPathResource("fixtures/yahoo/" + name).getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("maps a real Yahoo payload into the quote and the ordered bars, stamped with the injected clock")
	void mapsQuoteAndHistory() throws Exception {
		server.expect(requestTo(AAPL_URI))
				.andExpect(header(HttpHeaders.USER_AGENT, "test-agent"))
				.andRespond(withSuccess(fixture("aapl-1mo-1d.json"), MediaType.APPLICATION_JSON));

		StockSnapshot snapshot = provider.fetchSnapshot("AAPL", "1mo", "1d");

		StockQuote quote = snapshot.quote();
		assertThat(quote.symbol()).isEqualTo("AAPL");
		assertThat(quote.companyName()).isEqualTo("Apple Inc.");
		assertThat(quote.currency()).isEqualTo("USD");
		assertThat(quote.exchange()).isEqualTo("NasdaqGS");
		assertThat(quote.exchangeTimezone()).isEqualTo("America/New_York");
		assertThat(quote.price()).isEqualByComparingTo("310.44");
		assertThat(quote.previousClose()).isEqualByComparingTo("305.5899963378906");
		assertThat(quote.previousClose()).isNotEqualByComparingTo("333.74");
		assertThat(quote.dayHigh()).isEqualByComparingTo("311.49");
		assertThat(quote.dayLow()).isEqualByComparingTo("305.74");
		assertThat(quote.volume()).isEqualTo(30714033L);
		assertThat(quote.marketTime()).isEqualTo(Instant.ofEpochSecond(1787079592L));
		assertThat(quote.retrievedAt()).isEqualTo(NOW);

		List<PriceBar> history = snapshot.history();
		assertThat(history).hasSize(22);
		PriceBar first = history.getFirst();
		assertThat(first.timestamp()).isEqualTo(Instant.ofEpochSecond(1784554200L));
		assertThat(first.open()).isEqualByComparingTo("333.510009765625");
		assertThat(first.high()).isEqualByComparingTo("333.7099914550781");
		assertThat(first.low()).isEqualByComparingTo("323.67999267578125");
		assertThat(first.close()).isEqualByComparingTo("326.5899963378906");
		assertThat(first.volume()).isEqualTo(53468000L);
		assertThat(history).isSortedAccordingTo(Comparator.comparing(PriceBar::timestamp));
		server.verify();
	}

	@Test
	@DisplayName("trims and upper-cases the symbol, and falls back to the defaults when it is blank")
	void buildsTheRequest() throws Exception {
		expect(AAPL_URI, withSuccess(fixture("aapl-1mo-1d.json"), MediaType.APPLICATION_JSON));
		expect(AAPL_URI, withSuccess(fixture("aapl-1mo-1d.json"), MediaType.APPLICATION_JSON));

		provider.fetchSnapshot("  aapl  ", "1mo", "1d");
		provider.fetchSnapshot("  ", null, "");

		server.verify();
	}

	@Test
	@DisplayName("reads payloads that are well-formed but incomplete without failing the retrieval")
	void toleratesIncompletePayloads() throws Exception {
		expect(AAPL_URI, withSuccess(fixture("aapl-incomplete-bars.json"), MediaType.APPLICATION_JSON));

		StockSnapshot incomplete = provider.fetchSnapshot("AAPL", "1mo", "1d");
		List<PriceBar> history = incomplete.history();

		assertThat(incomplete.quote().previousClose()).isEqualByComparingTo("326.59");
		assertThat(history).extracting(PriceBar::timestamp)
				.containsExactly(Instant.ofEpochSecond(1784554200L), Instant.ofEpochSecond(1784727000L));
		assertThat(history).allSatisfy(bar -> assertThat(bar.close()).isNotNull());
		PriceBar shortColumns = history.getLast();
		assertThat(shortColumns.close()).isEqualByComparingTo("325.89");
		assertThat(shortColumns.high()).isEqualByComparingTo("329.0");
		assertThat(shortColumns.open()).isNull();
		assertThat(shortColumns.volume()).isNull();

		freshProvider();
		expect(AAPL_URI, withSuccess("""
				{"chart":{"result":[{"meta":{"symbol":"AAPL","regularMarketPrice":310.44}}],"error":null}}
				""", MediaType.APPLICATION_JSON));

		StockSnapshot snapshot = provider.fetchSnapshot("AAPL", "1mo", "1d");

		assertThat(snapshot.history()).isEmpty();
		assertThat(snapshot.quote().price()).isEqualByComparingTo(new BigDecimal("310.44"));
		assertThat(snapshot.quote().previousClose()).isNull();
	}

	@Test
	@DisplayName("the previous close is the session before the current one, and is null when there is no such session")
	void previousCloseIsThePriorSession() throws Exception {
		expect(BASE_URL + "/AAPL?range=1d&interval=1d", withSuccess("""
				{"chart":{"result":[{"meta":{"symbol":"AAPL","regularMarketPrice":310.44},
				"timestamp":[1784554200],
				"indicators":{"quote":[{"close":[326.59]}]}}],"error":null}}
				""", MediaType.APPLICATION_JSON));

		assertThat(provider.fetchSnapshot("AAPL", "1d", "1d").quote().previousClose()).isNull();

		freshProvider();
		expect(BASE_URL + "/AAPL?range=1d&interval=1h", withSuccess("""
				{"chart":{"result":[{"meta":{"symbol":"AAPL","regularMarketPrice":310.44},
				"timestamp":[1784554200,1784557800],
				"indicators":{"quote":[{"close":[326.59,327.10]}]}}],"error":null}}
				""", MediaType.APPLICATION_JSON));

		assertThat(provider.fetchSnapshot("AAPL", "1d", "1h").quote().previousClose()).isNull();

		freshProvider();
		expect(BASE_URL + "/AAPL?range=5d&interval=1d", withSuccess("""
				{"chart":{"result":[{"meta":{"symbol":"AAPL","regularMarketPrice":316.83,
				"chartPreviousClose":302.25},
				"timestamp":[1786714200,1786973400,1787059800],
				"indicators":{"quote":[{"close":[305.59,310.03,316.83]}]}}],"error":null}}
				""", MediaType.APPLICATION_JSON));

		assertThat(provider.fetchSnapshot("AAPL", "5d", "1d").quote().previousClose())
				.isEqualByComparingTo("310.03");
	}

	@Test
	@DisplayName("treats a payload with no usable price as an outage rather than inventing data")
	void unusablePayloadIsUnavailable() {
		Map<String, String> bodies = new LinkedHashMap<>();
		bodies.put("{}", "empty response");
		bodies.put("{\"chart\":{\"result\":[],\"error\":null}}", "no result");
		bodies.put("{\"chart\":{\"result\":[{\"meta\":{\"symbol\":\"AAPL\"}}],\"error\":null}}", "no current price");

		bodies.forEach((body, expectedMessage) -> {
			freshProvider();
			expect(AAPL_URI, withSuccess(body, MediaType.APPLICATION_JSON));

			assertThatExceptionOfType(StockDataUnavailableException.class)
					.as("payload %s", body)
					.isThrownBy(() -> provider.fetchSnapshot("AAPL", "1mo", "1d"))
					.withMessageContaining(expectedMessage);
		});
	}

	@Test
	@DisplayName("maps each upstream failure to one the caller can act on: unknown symbol, bad request, outage")
	void mapsFailureStatuses() throws Exception {
		Map<HttpStatus, Class<? extends RuntimeException>> expected = new LinkedHashMap<>();
		expected.put(HttpStatus.NOT_FOUND, SymbolNotFoundException.class);
		expected.put(HttpStatus.INTERNAL_SERVER_ERROR, StockDataUnavailableException.class);
		expected.put(HttpStatus.BAD_GATEWAY, StockDataUnavailableException.class);
		expected.put(HttpStatus.TOO_MANY_REQUESTS, StockDataUnavailableException.class);
		expected.put(HttpStatus.BAD_REQUEST, InvalidStockRequestException.class);
		expected.put(HttpStatus.UNAUTHORIZED, InvalidStockRequestException.class);
		expected.put(HttpStatus.FORBIDDEN, InvalidStockRequestException.class);

		expected.forEach((status, exception) -> {
			freshProvider();
			expect(AAPL_URI, withStatus(status));

			assertThatExceptionOfType(exception)
					.as("HTTP %s", status.value())
					.isThrownBy(() -> provider.fetchSnapshot("AAPL", "1mo", "1d"));
		});

		freshProvider();
		expect(BASE_URL + "/ZZZZ?range=1mo&interval=1d",
				withSuccess(fixture("unknown-symbol-404.json"), MediaType.APPLICATION_JSON));

		assertThatExceptionOfType(SymbolNotFoundException.class)
				.isThrownBy(() -> provider.fetchSnapshot("ZZZZ", "1mo", "1d"))
				.satisfies(ex -> assertThat(ex.getSymbol()).isEqualTo("ZZZZ"));
	}

	@Test
	@DisplayName("maps an unreachable upstream and an unparseable body to a retryable outage")
	void transportAndParsingFailuresAreRetryable() {
		expect(AAPL_URI, withException(new SocketTimeoutException("Read timed out")));

		assertThatExceptionOfType(StockDataUnavailableException.class)
				.isThrownBy(() -> provider.fetchSnapshot("AAPL", "1mo", "1d"))
				.withMessageContaining("Could not reach");

		freshProvider();
		expect(AAPL_URI, withSuccess("{\"chart\": {{{ not json", MediaType.APPLICATION_JSON));

		assertThatExceptionOfType(StockDataUnavailableException.class)
				.isThrownBy(() -> provider.fetchSnapshot("AAPL", "1mo", "1d"));
	}
}
