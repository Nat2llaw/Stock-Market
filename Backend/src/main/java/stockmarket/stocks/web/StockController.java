package stockmarket.stocks.web;

import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import stockmarket.stocks.config.YahooFinanceProperties;
import stockmarket.stocks.error.NoStoredDataException;
import stockmarket.stocks.persistence.entity.StockPriceBarEntity;
import stockmarket.stocks.persistence.entity.StockQuoteEntity;
import stockmarket.stocks.service.StockStorageService;
import stockmarket.stocks.web.dto.PriceBarResponse;
import stockmarket.stocks.web.dto.QuoteResponse;
import stockmarket.stocks.web.dto.StockOverviewResponse;

@RestController
@RequestMapping("/stocks")
public class StockController {

    private static final String SYMBOL_PATTERN = "[A-Za-z0-9.^=-]+";
	
    private final StockStorageService storageService;
    private final YahooFinanceProperties properties;
    
    
	public StockController(StockStorageService storageService, YahooFinanceProperties properties) {
		this.storageService = storageService;
		this.properties = properties;
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

    private String intervalOrDefault(String interval) {
        return interval == null || interval.isBlank() ? properties.defaultInterval() : interval;
    }
    
    private static String normalise(String symbol) {
        return symbol.strip().toUpperCase(Locale.ROOT);
    }
    
    private static List<PriceBarResponse> toResponses(List<StockPriceBarEntity> bars) {
		return bars.stream().map(PriceBarResponse::from).toList();
	}
    
    private StockQuoteEntity requireQuote(String symbol) {
		return storageService.latestQuote(symbol).orElseThrow(() -> new NoStoredDataException(symbol));
	}


}
