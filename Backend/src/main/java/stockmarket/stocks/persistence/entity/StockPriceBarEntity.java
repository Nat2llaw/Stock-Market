package stockmarket.stocks.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "stock_price_bar",
		uniqueConstraints = @UniqueConstraint(name = "uq_stock_price_bar_symbol_interval_timestamp",
				columnNames = { "symbol", "bar_interval", "bar_timestamp" }))
public class StockPriceBarEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 16)
	private String symbol;

	@Column(name = "bar_timestamp", nullable = false)
	private Instant barTimestamp;

	@Column(name = "bar_interval", nullable = false, length = 8)
	private String barInterval;

	@Column(name = "open_price", precision = 19, scale = 4)
	private BigDecimal open;

	@Column(name = "high_price", precision = 19, scale = 4)
	private BigDecimal high;

	@Column(name = "low_price", precision = 19, scale = 4)
	private BigDecimal low;

	@Column(name = "close_price", nullable = false, precision = 19, scale = 4)
	private BigDecimal close;

	private Long volume;

	@Column(name = "retrieved_at", nullable = false)
	private Instant retrievedAt;

	protected StockPriceBarEntity() {
		// for JPA
	}

	public StockPriceBarEntity(String symbol, Instant barTimestamp, String barInterval, BigDecimal open,
			BigDecimal high, BigDecimal low, BigDecimal close, Long volume, Instant retrievedAt) {
		this.symbol = symbol;
		this.barTimestamp = barTimestamp;
		this.barInterval = barInterval;
		this.open = open;
		this.high = high;
		this.low = low;
		this.close = close;
		this.volume = volume;
		this.retrievedAt = retrievedAt;
	}

	public Long getId() {
		return id;
	}

	public String getSymbol() {
		return symbol;
	}

	public Instant getBarTimestamp() {
		return barTimestamp;
	}

	public String getBarInterval() {
		return barInterval;
	}

	public BigDecimal getOpen() {
		return open;
	}

	public BigDecimal getHigh() {
		return high;
	}

	public BigDecimal getLow() {
		return low;
	}

	public BigDecimal getClose() {
		return close;
	}

	public Long getVolume() {
		return volume;
	}

	public Instant getRetrievedAt() {
		return retrievedAt;
	}
}
