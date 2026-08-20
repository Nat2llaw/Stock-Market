package stockmarket.stocks.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

	private static final EmbeddedPostgres POSTGRES = start();

	private static EmbeddedPostgres start() {
		try {
			return EmbeddedPostgres.start();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not start the embedded PostgreSQL server", ex);
		}
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
		registry.add("spring.datasource.username", () -> "postgres");
		registry.add("spring.datasource.password", () -> "");
	}
}
