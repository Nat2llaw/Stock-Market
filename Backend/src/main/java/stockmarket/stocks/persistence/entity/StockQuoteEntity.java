package stockmarket.stocks.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_quote")
public class StockQuoteEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 16)
	private String symbol;

	@Column(name = "company_name")
	private String companyName;

	@Column(length = 8)
	private String currency;

	@Column(length = 64)
	private String exchange;

	@Column(name = "exchange_timezone", length = 64)
	private String exchangeTimezone;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal price;

	@Column(name = "previous_close", precision = 19, scale = 4)
	private BigDecimal previousClose;

	@Column(name = "day_high", precision = 19, scale = 4)
	private BigDecimal dayHigh;

	@Column(name = "day_low", precision = 19, scale = 4)
	private BigDecimal dayLow;

	private Long volume;

	@Column(name = "market_time")
	private Instant marketTime;

	@Column(name = "retrieved_at", nullable = false)
	private Instant retrievedAt;

	protected StockQuoteEntity() {
		// for JPA
	}

	public StockQuoteEntity(String symbol, String companyName, String currency, String exchange,
			String exchangeTimezone, BigDecimal price, BigDecimal previousClose, BigDecimal dayHigh,
			BigDecimal dayLow, Long volume, Instant marketTime, Instant retrievedAt) {
		this.symbol = symbol;
		this.companyName = companyName;
		this.currency = currency;
		this.exchange = exchange;
		this.exchangeTimezone = exchangeTimezone;
		this.price = price;
		this.previousClose = previousClose;
		this.dayHigh = dayHigh;
		this.dayLow = dayLow;
		this.volume = volume;
		this.marketTime = marketTime;
		this.retrievedAt = retrievedAt;
	}

	public Long getId() {
		return id;
	}

	public String getSymbol() {
		return symbol;
	}

	public String getCompanyName() {
		return companyName;
	}

	public String getCurrency() {
		return currency;
	}

	public String getExchange() {
		return exchange;
	}

	public String getExchangeTimezone() {
		return exchangeTimezone;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public BigDecimal getPreviousClose() {
		return previousClose;
	}

	public BigDecimal getDayHigh() {
		return dayHigh;
	}

	public BigDecimal getDayLow() {
		return dayLow;
	}

	public Long getVolume() {
		return volume;
	}

	public Instant getMarketTime() {
		return marketTime;
	}

	public Instant getRetrievedAt() {
		return retrievedAt;
	}
}
