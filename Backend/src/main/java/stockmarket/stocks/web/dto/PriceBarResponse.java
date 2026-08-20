package stockmarket.stocks.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

import stockmarket.stocks.persistence.entity.StockPriceBarEntity;

public record PriceBarResponse(
		Instant timestamp,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal open,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal high,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal low,
		@JsonFormat(shape = JsonFormat.Shape.STRING)
		BigDecimal close,
		Long volume,
		Instant retrievedAt) {

	public static PriceBarResponse from(StockPriceBarEntity entity) {
		return new PriceBarResponse(
				entity.getBarTimestamp(),
				Money.scaled(entity.getOpen()),
				Money.scaled(entity.getHigh()),
				Money.scaled(entity.getLow()),
				Money.scaled(entity.getClose()),
				entity.getVolume(),
				entity.getRetrievedAt());
	}
}
