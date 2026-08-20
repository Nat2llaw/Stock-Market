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
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setType(URI.create("urn:stocks:symbol-not-found"));
		problem.setTitle("Symbol not found");
		problem.setProperty("symbol", ex.getSymbol());
		return problem;
	}

	@ExceptionHandler(NoStoredDataException.class)
	public ProblemDetail handleNoStoredData(NoStoredDataException ex, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setType(URI.create("urn:stocks:no-stored-data"));
		problem.setTitle("No data collected yet");
		problem.setProperty("symbol", ex.getSymbol());
		problem.setProperty("hint",
				"POST " + request.getContextPath() + "/stocks/" + ex.getSymbol() + "/refresh to retrieve it now");
		return problem;
	}

	@ExceptionHandler(InvalidStockRequestException.class)
	public ProblemDetail handleInvalidRequest(InvalidStockRequestException ex) {
		log.warn("Upstream rejected the request for {}: {}", ex.getSymbol(), ex.getMessage());

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"The stock data provider rejected the request. Check the range and interval.");
		problem.setType(URI.create("urn:stocks:invalid-request"));
		problem.setTitle("Invalid request");
		problem.setProperty("symbol", ex.getSymbol());
		return problem;
	}

	@ExceptionHandler(StockDataUnavailableException.class)
	public ProblemDetail handleUnavailable(StockDataUnavailableException ex) {
		log.warn("Upstream unavailable for {}: {}", ex.getSymbol(), ex.getMessage());

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The stock data provider could not be reached. Any stored data remains available.");
		problem.setType(URI.create("urn:stocks:upstream-unavailable"));
		problem.setTitle("Stock data provider unavailable");
		problem.setProperty("symbol", ex.getSymbol());
		problem.setProperty("retryAfterSeconds", 30);
		return problem;
	}
}
