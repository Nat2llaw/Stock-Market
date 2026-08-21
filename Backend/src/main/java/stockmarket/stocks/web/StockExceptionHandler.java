package stockmarket.stocks.web;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import stockmarket.stocks.error.InvalidStockRequestException;
import stockmarket.stocks.error.NoStoredDataException;
import stockmarket.stocks.error.StockDataUnavailableException;
import stockmarket.stocks.error.SymbolNotFoundException;

@RestControllerAdvice
public class StockExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(StockExceptionHandler.class);

	@ExceptionHandler(SymbolNotFoundException.class)
	public ProblemDetail handleSymbolNotFound(SymbolNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "symbol-not-found", "Symbol not found", ex.getMessage(), ex.getSymbol());
	}

	@ExceptionHandler(NoStoredDataException.class)
	public ProblemDetail handleNoStoredData(NoStoredDataException ex, HttpServletRequest request) {
		ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "no-stored-data", "No data collected yet",
				ex.getMessage(), ex.getSymbol());
		problem.setProperty("hint",
				"POST " + request.getContextPath() + "/stocks/" + ex.getSymbol() + "/refresh to retrieve it now");
		return problem;
	}

	@ExceptionHandler(InvalidStockRequestException.class)
	public ProblemDetail handleInvalidRequest(InvalidStockRequestException ex) {
		log.warn("Upstream rejected the request for {}: {}", ex.getSymbol(), ex.getMessage());

		return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
				"The stock data provider rejected the request. Check the range and interval.", ex.getSymbol());
	}

	@ExceptionHandler(StockDataUnavailableException.class)
	public ProblemDetail handleUnavailable(StockDataUnavailableException ex) {
		log.warn("Upstream unavailable for {}: {}", ex.getSymbol(), ex.getMessage());

		ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "upstream-unavailable",
				"Stock data provider unavailable",
				"The stock data provider could not be reached. Any stored data remains available.", ex.getSymbol());
		problem.setProperty("retryAfterSeconds", 30);
		return problem;
	}

	/**
	 * The upstream's own message is logged, never returned: a detail is only passed in here when
	 * it is safe for a client to read.
	 */
	private static ProblemDetail problem(HttpStatus status, String type, String title, String detail, String symbol) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("urn:stocks:" + type));
		problem.setTitle(title);
		problem.setProperty("symbol", symbol);
		return problem;
	}
}
