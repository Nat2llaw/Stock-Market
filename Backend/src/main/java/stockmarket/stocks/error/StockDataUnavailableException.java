package stockmarket.stocks.error;

public class StockDataUnavailableException extends StockDataException {

	public StockDataUnavailableException(String symbol, String message) {
		super(symbol, message);
	}

	public StockDataUnavailableException(String symbol, String message, Throwable cause) {
		super(symbol, message, cause);
	}
}
