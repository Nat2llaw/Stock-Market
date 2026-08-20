package stockmarket.stocks.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceBar(
		Instant timestamp,
		BigDecimal open,
		BigDecimal high,
		BigDecimal low,
		BigDecimal close,
		Long volume) {
}
