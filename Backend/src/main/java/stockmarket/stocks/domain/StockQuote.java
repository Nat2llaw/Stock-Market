package stockmarket.stocks.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record StockQuote(
		String symbol,
		String companyName,
		String currency,
		String exchange,
		String exchangeTimezone,
		BigDecimal price,
		BigDecimal previousClose,
		BigDecimal dayHigh,
		BigDecimal dayLow,
		Long volume,
		Instant marketTime,
		Instant retrievedAt) {
}

