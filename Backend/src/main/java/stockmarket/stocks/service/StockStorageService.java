package stockmarket.stocks.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import stockmarket.stocks.domain.PriceBar;
import stockmarket.stocks.domain.StockQuote;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.persistence.entity.StockPriceBarEntity;
import stockmarket.stocks.persistence.entity.StockQuoteEntity;
import stockmarket.stocks.persistence.repository.StockPriceBarRepository;
import stockmarket.stocks.persistence.repository.StockQuoteRepository;

@Service
public class StockStorageService {

	private static final Logger log = LoggerFactory.getLogger(StockStorageService.class);

	private final StockQuoteRepository quoteRepository;
	private final StockPriceBarRepository barRepository;

	public StockStorageService(StockQuoteRepository quoteRepository, StockPriceBarRepository barRepository) {
		this.quoteRepository = quoteRepository;
		this.barRepository = barRepository;
	}

	@Transactional
	public StockQuoteEntity store(StockSnapshot snapshot, String interval) {
		StockQuoteEntity quote = storeQuote(snapshot.quote());
		int bars = storeBars(snapshot.quote().symbol(), snapshot.history(), interval,
				snapshot.quote().retrievedAt());
		log.debug("Stored quote for {} and upserted {} {} bars", snapshot.quote().symbol(), bars, interval);
		return quote;
	}

	private StockQuoteEntity storeQuote(StockQuote quote) {
		return quoteRepository.save(new StockQuoteEntity(
				quote.symbol(),
				quote.companyName(),
				quote.currency(),
				quote.exchange(),
				quote.exchangeTimezone(),
				quote.price(),
				quote.previousClose(),
				quote.dayHigh(),
				quote.dayLow(),
				quote.volume(),
				quote.marketTime(),
				quote.retrievedAt()));
	}

	private int storeBars(String symbol, List<PriceBar> history, String interval, Instant retrievedAt) {
		for (PriceBar bar : history) {
			barRepository.upsert(symbol, bar.timestamp(), interval, bar.open(), bar.high(), bar.low(), bar.close(),
					bar.volume(), retrievedAt);
		}
		return history.size();
	}

	@Transactional(readOnly = true)
	public Optional<StockQuoteEntity> latestQuote(String symbol) {
		return quoteRepository.findFirstBySymbolOrderByRetrievedAtDescIdDesc(symbol);
	}

	@Transactional(readOnly = true)
	public List<StockPriceBarEntity> history(String symbol, String interval) {
		return barRepository.findBySymbolAndBarIntervalOrderByBarTimestampDesc(symbol, interval);
	}
}
