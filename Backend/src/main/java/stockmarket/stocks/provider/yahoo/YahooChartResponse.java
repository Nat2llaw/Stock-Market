package stockmarket.stocks.provider.yahoo;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Chart(List<Result> result, ChartError error) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Result(Meta meta, List<Long> timestamp, Indicators indicators) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Meta(
			String symbol,
			String currency,
			String exchangeName,
			String fullExchangeName,
			String exchangeTimezoneName,
			String longName,
			String shortName,
			BigDecimal regularMarketPrice,
			BigDecimal regularMarketDayHigh,
			BigDecimal regularMarketDayLow,
			Long regularMarketVolume,
			Long regularMarketTime) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Indicators(List<Quote> quote) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Quote(
			List<BigDecimal> open,
			List<BigDecimal> high,
			List<BigDecimal> low,
			List<BigDecimal> close,
			List<Long> volume) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChartError(String code, String description) {
	}
}
