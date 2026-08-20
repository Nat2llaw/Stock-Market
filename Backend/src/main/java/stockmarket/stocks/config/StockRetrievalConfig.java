package stockmarket.stocks.config;

import java.time.Clock;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import stockmarket.stocks.error.StockDataUnavailableException;


@Configuration
public class StockRetrievalConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	public RestClient yahooRestClient(RestClient.Builder builder, YahooFinanceProperties properties) {
		var requestFactory = ClientHttpRequestFactoryBuilder.detect()
				.build(HttpClientSettings.defaults()
						.withConnectTimeout(properties.connectTimeout())
						.withReadTimeout(properties.readTimeout()));

		return builder
				.baseUrl(properties.baseUrl())
				.defaultHeader(HttpHeaders.ACCEPT, "application/json")
				.requestFactory(requestFactory)
				.build();
	}

	@Bean
	public RetryTemplate stockRetrievalRetryTemplate(YahooFinanceProperties properties) {
		RetryPolicy policy = RetryPolicy.builder()
				.maxRetries(properties.maxRetries())
				.delay(properties.retryDelay())
				.multiplier(properties.retryMultiplier())
				.maxDelay(properties.maxRetryDelay())
				.includes(StockDataUnavailableException.class)
				.build();

		return new RetryTemplate(policy);
	}
}
