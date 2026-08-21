package stockmarket.stocks.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A ticker symbol: constrained to the width of the {@code varchar(16)} column it lands in, and
 * to the shape real symbols take — letters, digits, and the punctuation they use ({@code BRK-B},
 * {@code BRK.B}, {@code ^GSPC}, {@code BTC-USD}, {@code ES=F}).
 * <p>
 * Composed once rather than repeated per handler, so every endpoint is constrained identically
 * by construction and a new endpoint cannot be added with a weaker rule by accident.
 */
@NotBlank
@Size(max = 16)
@Pattern(regexp = "[A-Za-z0-9.^=-]+")
@Constraint(validatedBy = {})
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Ticker {

	String message() default "must be a ticker symbol of at most 16 characters";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
