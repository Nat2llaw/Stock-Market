package stockmarket.stocks.retrieval;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import stockmarket.stocks.config.StockRetrievalConfig;
import stockmarket.stocks.config.YahooFinanceProperties;
import stockmarket.stocks.provider.StockDataProvider;
import stockmarket.stocks.provider.yahoo.YahooFinanceStockDataProvider;
import stockmarket.stocks.service.StockRetrievalService;

@SpringBootTest(classes = StockRetrievalWiringIntegrationTest.RetrievalSlice.class)
class StockRetrievalWiringIntegrationTest {

	@EnableConfigurationProperties(YahooFinanceProperties.class)
	@ImportAutoConfiguration(RestClientAutoConfiguration.class)
	@Import({ StockRetrievalConfig.class, YahooFinanceStockDataProvider.class,
			StockRetrievalService.class })
	static class RetrievalSlice {
	}

	@Autowired
	private StockRetrievalService service;

	@Autowired
	private StockDataProvider provider;

	@Autowired
	private YahooFinanceProperties properties;

	@Test
	@DisplayName("the collaborators are injected and the shipped configuration binds to the v8 chart endpoint")
	void wiresRetrievalFromTheShippedConfiguration() {
		assertThat(service).isNotNull();
		assertThat(provider).isInstanceOf(YahooFinanceStockDataProvider.class);

		assertThat(properties.baseUrl()).isEqualTo("https://query1.finance.yahoo.com/v8/finance/chart");
		assertThat(properties.defaultSymbol()).isEqualTo("AAPL");
		assertThat(properties.defaultRange()).isEqualTo("1mo");
		assertThat(properties.defaultInterval()).isEqualTo("1d");

		assertThat(properties.connectTimeout()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(30));
		assertThat(properties.readTimeout()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(30));
		assertThat(properties.maxRetries()).isPositive().isLessThanOrEqualTo(10);
	}
}
