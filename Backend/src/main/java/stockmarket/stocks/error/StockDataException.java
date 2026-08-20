package stockmarket.stocks.error;

public abstract class StockDataException extends RuntimeException {

	private final String symbol;

	protected StockDataException(String symbol, String message) {
		super(message);
		this.symbol = symbol;
	}

	protected StockDataException(String symbol, String message, Throwable cause) {
		super(message, cause);
		this.symbol = symbol;
	}

	public String getSymbol() {
		return symbol;
	}
}
