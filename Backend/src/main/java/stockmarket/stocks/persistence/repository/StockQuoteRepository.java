package stockmarket.stocks.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import stockmarket.stocks.persistence.entity.StockQuoteEntity;

public interface StockQuoteRepository extends JpaRepository<StockQuoteEntity, Long> {

	Optional<StockQuoteEntity> findFirstBySymbolOrderByRetrievedAtDescIdDesc(@Param("symbol") String symbol);
}
