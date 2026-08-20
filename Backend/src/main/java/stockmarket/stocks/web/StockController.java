package stockmarket.stocks.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import stockmarket.stocks.config.YahooFinanceProperties;

@RestController
@RequestMapping("/stocks")
public class StockController {

	private final YahooFinanceProperties properties;

	public StockController(YahooFinanceProperties properties) {
		this.properties = properties;
	}

	@GetMapping("/default")
	public String defaultSymbol() {
		return properties.defaultSymbol();
	}
}
