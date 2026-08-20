package stockmarket.stocks.error;

public class NoStoredDataException extends RuntimeException {

	private final String symbol;

	public NoStoredDataException(String symbol) {
		super("No stored data for symbol '" + symbol + "' yet");
		this.symbol = symbol;
	}

	public String getSymbol() {
		return symbol;
	}
}
