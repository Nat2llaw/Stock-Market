package stockmarket.stocks.error;

public class SymbolNotFoundException extends StockDataException {

	public SymbolNotFoundException(String symbol) {
		super(symbol, "No stock data found for symbol '" + symbol + "'; it may be delisted or misspelled");
	}
}
