package stockmarket.stocks.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Money {

	private static final int STORAGE_SCALE = 4;

	private Money() {
	}

	static BigDecimal scaled(BigDecimal value) {
		return value == null ? null : value.setScale(STORAGE_SCALE, RoundingMode.HALF_UP);
	}
}
