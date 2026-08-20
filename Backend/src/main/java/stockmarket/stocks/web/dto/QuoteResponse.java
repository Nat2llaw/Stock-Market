package stockmarket.stocks.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

import stockmarket.stocks.persistence.entity.StockQuoteEntity;

public record QuoteResponse(
		String symbol,
		String companyName,
		String currency,
		String exchange,
		String exchangeTimezone,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal price,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal previousClose,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal dayHigh,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal dayLow,
		Long volume,
		Instant marketTime,
		Instant retrievedAt) {

	public static QuoteResponse from(StockQuoteEntity entity) {
		return new QuoteResponse(
				entity.getSymbol(),
				entity.getCompanyName(),
				entity.getCurrency(),
				entity.getExchange(),
				entity.getExchangeTimezone(),
				Money.scaled(entity.getPrice()),
				Money.scaled(entity.getPreviousClose()),
				Money.scaled(entity.getDayHigh()),
				Money.scaled(entity.getDayLow()),
				entity.getVolume(),
				entity.getMarketTime(),
				entity.getRetrievedAt());
	}
}
