package it.exprivia.utentiservice.dto;

import it.exprivia.utenti.dto.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterAll
    static void tearDown() {
        validator = null;
    }

    @Test
    void email_aziendaConFormatiRealiRestaValida() {
        assertThat(hasEmailViolations("mario.de-santis@exprivia.com")).isFalse();
        assertThat(hasEmailViolations("m.rossi@exprivia.com")).isFalse();
        assertThat(hasEmailViolations("Mario.Rossi2@Exprivia.com")).isFalse();
        assertThat(hasEmailViolations("anna_maria.verdi@exprivia.com")).isFalse();
    }

    @Test
    void email_fuoriDominioOAFormatoNonValido_vieneRifiutata() {
        assertThat(hasEmailViolations("mario.rossi@gmail.com")).isTrue();
        assertThat(hasEmailViolations("mario..rossi@exprivia.com")).isTrue();
        assertThat(hasEmailViolations("@exprivia.com")).isTrue();
    }

    private boolean hasEmailViolations(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Mario Rossi");
        request.setEmail(email);
        request.setPassword("password123");
        return validator.validate(request).stream()
                .anyMatch(violation -> "email".equals(violation.getPropertyPath().toString()));
    }
}
