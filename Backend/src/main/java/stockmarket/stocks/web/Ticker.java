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
