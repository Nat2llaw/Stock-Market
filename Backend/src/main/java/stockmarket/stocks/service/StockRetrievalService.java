package stockmarket.stocks.service;

import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

import stockmarket.stocks.config.YahooFinanceProperties;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.error.StockDataException;
import stockmarket.stocks.error.StockDataUnavailableException;
import stockmarket.stocks.provider.StockDataProvider;

@Service
public class StockRetrievalService {

	private final StockDataProvider provider;
	private final RetryTemplate retryTemplate;
	private final YahooFinanceProperties properties;

	public StockRetrievalService(StockDataProvider provider, RetryTemplate stockRetrievalRetryTemplate,
			YahooFinanceProperties properties) {
		this.provider = provider;
		this.retryTemplate = stockRetrievalRetryTemplate;
		this.properties = properties;
	}

	public StockSnapshot fetchDefaultSnapshot() {
		return fetchSnapshot(properties.defaultSymbol());
	}

	public StockSnapshot fetchSnapshot(String symbol) {
		return fetchSnapshot(symbol, properties.defaultRange(), properties.defaultInterval());
	}

	public StockSnapshot fetchSnapshot(String symbol, String range, String interval) {
		try {
			return retryTemplate.execute(() -> provider.fetchSnapshot(symbol, range, interval));
		}
		catch (RetryException ex) {
			throw unwrap(symbol, ex);
		}
	}

	private StockDataException unwrap(String symbol, RetryException ex) {
		Throwable cause = ex.getCause();
		if (cause instanceof StockDataException stockDataException) {
			return stockDataException;
		}
		return new StockDataUnavailableException(symbol,
				"Gave up retrieving stock data for '" + symbol + "': " + ex.getMessage(), ex);
	}
}
