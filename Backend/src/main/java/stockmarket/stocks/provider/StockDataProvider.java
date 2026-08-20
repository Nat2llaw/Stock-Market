package stockmarket.stocks.provider;

import stockmarket.stocks.domain.StockSnapshot;

public interface StockDataProvider {

	StockSnapshot fetchSnapshot(String symbol, String range, String interval);

}
