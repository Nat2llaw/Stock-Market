package stockmarket.stocks.persistence.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import stockmarket.stocks.persistence.entity.StockPriceBarEntity;

public interface StockPriceBarRepository extends JpaRepository<StockPriceBarEntity, Long> {

	List<StockPriceBarEntity> findBySymbolAndBarIntervalOrderByBarTimestampDesc(String symbol, String barInterval);

	List<StockPriceBarEntity> findBySymbolAndBarIntervalAndBarTimestampBetweenOrderByBarTimestampDesc(
			String symbol, String barInterval, Instant from, Instant to);

	@Modifying
	@Query(value = """
			insert into stock_price_bar
			    (symbol, bar_timestamp, bar_interval, open_price, high_price, low_price, close_price, volume, retrieved_at)
			values
			    (:symbol, :barTimestamp, :barInterval, :open, :high, :low, :close, :volume, :retrievedAt)
			on conflict on constraint uq_stock_price_bar_symbol_interval_timestamp do update set
			    open_price   = excluded.open_price,
			    high_price   = excluded.high_price,
			    low_price    = excluded.low_price,
			    close_price  = excluded.close_price,
			    volume       = excluded.volume,
			    retrieved_at = excluded.retrieved_at
			""", nativeQuery = true)
	void upsert(@Param("symbol") String symbol,
			@Param("barTimestamp") Instant barTimestamp,
			@Param("barInterval") String barInterval,
			@Param("open") BigDecimal open,
			@Param("high") BigDecimal high,
			@Param("low") BigDecimal low,
			@Param("close") BigDecimal close,
			@Param("volume") Long volume,
			@Param("retrievedAt") Instant retrievedAt);
}
