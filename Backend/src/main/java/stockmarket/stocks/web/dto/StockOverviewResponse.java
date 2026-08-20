package stockmarket.stocks.web.dto;

import java.util.List;

public record StockOverviewResponse(
		String symbol,
		String interval,
		QuoteResponse quote,
		List<PriceBarResponse> history) {
}
