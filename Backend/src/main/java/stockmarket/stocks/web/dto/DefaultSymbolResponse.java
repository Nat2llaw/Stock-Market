package stockmarket.stocks.web.dto;

/**
 * The ticker this deployment monitors. A one-field object rather than a bare string so that
 * every endpoint answers with JSON: one content type for the client to handle, and room to name
 * a second field later without changing the media type.
 */
public record DefaultSymbolResponse(String symbol) {
}
