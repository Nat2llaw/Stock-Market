package stockmarket.stocks.domain;

import java.util.List;

public record StockSnapshot(StockQuote quote, List<PriceBar> history) {

	public StockSnapshot {
		history = history == null ? List.of() : List.copyOf(history);
	}
}
