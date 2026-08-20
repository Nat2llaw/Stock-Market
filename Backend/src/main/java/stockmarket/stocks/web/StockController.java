package stockmarket.stocks.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import stockmarket.stocks.config.YahooFinanceProperties;
import stockmarket.stocks.domain.StockSnapshot;
import stockmarket.stocks.error.NoStoredDataException;
import stockmarket.stocks.persistence.entity.StockPriceBarEntity;
import stockmarket.stocks.persistence.entity.StockQuoteEntity;
import stockmarket.stocks.service.StockRetrievalService;
import stockmarket.stocks.service.StockStorageService;
import stockmarket.stocks.web.dto.PriceBarResponse;
import stockmarket.stocks.web.dto.QuoteResponse;
import stockmarket.stocks.web.dto.StockOverviewResponse;

@RestController
@RequestMapping("/stocks")
public class StockController {

	private static final Logger log = LoggerFactory.getLogger(StockController.class);

	private static final String SYMBOL_PATTERN = "[A-Za-z0-9.^=-]+";

	private final StockStorageService storageService;
	private final StockRetrievalService retrievalService;
	private final YahooFinanceProperties properties;
	private final Clock clock;

	public StockController(StockStorageService storageService, StockRetrievalService retrievalService,
			YahooFinanceProperties properties, Clock clock) {
		this.storageService = storageService;
		this.retrievalService = retrievalService;
		this.properties = properties;
		this.clock = clock;
	}

	@GetMapping("/default")
	public String defaultSymbol() {
		return properties.defaultSymbol();
	}

	@GetMapping("/{symbol}")
	public StockOverviewResponse overview(
			@PathVariable @NotBlank @Size(max = 16) @Pattern(regexp = SYMBOL_PATTERN) String symbol,
			@RequestParam(required = false) String interval) {
		String normalisedSymbol = normalise(symbol);
		String effectiveInterval = intervalOrDefault(interval);

		return new StockOverviewResponse(
				normalisedSymbol,
				effectiveInterval,
				QuoteResponse.from(requireQuote(normalisedSymbol)),
				toResponses(storageService.history(normalisedSymbol, effectiveInterval)));
	}

	@GetMapping("/{symbol}/quote")
	public QuoteResponse quote(
			@PathVariable @NotBlank @Size(max = 16) @Pattern(regexp = SYMBOL_PATTERN) String symbol) {
		return QuoteResponse.from(requireQuote(normalise(symbol)));
	}

	@GetMapping("/{symbol}/history")
	public List<PriceBarResponse> history(
			@PathVariable @NotBlank @Size(max = 16) @Pattern(regexp = SYMBOL_PATTERN) String symbol,
			@RequestParam(required = false) String interval,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

		String normalisedSymbol = normalise(symbol);
		String effectiveInterval = intervalOrDefault(interval);

		if (from == null && to == null) {
			return toResponses(storageService.history(normalisedSymbol, effectiveInterval));
		}
		return toResponses(storageService.history(normalisedSymbol, effectiveInterval,
				from == null ? Instant.EPOCH : from,
				to == null ? Instant.now(clock) : to));
	}

	@PostMapping("/{symbol}/refresh")
	public ResponseEntity<StockOverviewResponse> refresh(
			@PathVariable @NotBlank @Size(max = 16) @Pattern(regexp = SYMBOL_PATTERN) String symbol,
			@RequestParam(required = false) String range,
			@RequestParam(required = false) @Size(max = 8) String interval) {

		String normalisedSymbol = normalise(symbol);
		String effectiveInterval = intervalOrDefault(interval);
		String effectiveRange = range == null || range.isBlank() ? properties.defaultRange() : range;

		StockSnapshot snapshot = retrievalService.fetchSnapshot(normalisedSymbol, effectiveRange, effectiveInterval);
		StockQuoteEntity stored = storageService.store(snapshot, effectiveInterval);
		log.info("Refreshed {} on request: {} bars", normalisedSymbol, snapshot.history().size());

		return ResponseEntity.ok(new StockOverviewResponse(
				normalisedSymbol,
				effectiveInterval,
				QuoteResponse.from(stored),
				toResponses(storageService.history(normalisedSymbol, effectiveInterval))));
	}

	private StockQuoteEntity requireQuote(String symbol) {
		return storageService.latestQuote(symbol).orElseThrow(() -> new NoStoredDataException(symbol));
	}

	private String intervalOrDefault(String interval) {
		return interval == null || interval.isBlank() ? properties.defaultInterval() : interval;
	}

	private static String normalise(String symbol) {
		return symbol.strip().toUpperCase(Locale.ROOT);
	}

	private static List<PriceBarResponse> toResponses(List<StockPriceBarEntity> bars) {
		return bars.stream().map(PriceBarResponse::from).toList();
	}
}
