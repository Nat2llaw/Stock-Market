package stockmarket.stocks.error;

public class InvalidStockRequestException extends StockDataException {

	public InvalidStockRequestException(String symbol, String message) {
		super(symbol, message);
	}
}
