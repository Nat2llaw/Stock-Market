package stockmarket.stocks.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Validated
@ConfigurationProperties(prefix = "app.yahoo-finance")
public record YahooFinanceProperties(

		@NotBlank String baseUrl,

		@DefaultValue("5s") Duration connectTimeout,

		@DefaultValue("10s") Duration readTimeout,

		@DefaultValue("3") @PositiveOrZero int maxRetries,

		@DefaultValue("500ms") Duration retryDelay,

		@DefaultValue("5s") Duration maxRetryDelay,

		@DefaultValue("2.0") @Positive double retryMultiplier,

		@DefaultValue("AAPL") @NotBlank String defaultSymbol,

		@DefaultValue("1mo") @NotBlank String defaultRange,

		@DefaultValue("1d") @NotBlank String defaultInterval,

		@DefaultValue("Mozilla/5.0 (compatible; OaklandStockMarket/1.0)") @NotBlank String userAgent) {
}
