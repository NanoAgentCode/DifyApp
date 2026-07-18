package com.github.app.dify.auth.req;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void registrationRequiresAValidEmailCodeAndStrongPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new-user");
        request.setEmail("user@example.com");
        request.setVerificationCode("123456");
        request.setPassword("password1");

        assertTrue(validator.validate(request).isEmpty());

        request.setVerificationCode("12345");
        request.setPassword("onlyletters");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void forgotPasswordRequiresEmailCodeAndStrongNewPassword() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@example.com");
        request.setVerificationCode("654321");
        request.setNewPassword("newPassword2");

        assertTrue(validator.validate(request).isEmpty());

        request.setEmail("invalid-email");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void changePasswordRequiresOriginalPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setNewPassword("newPassword2");
        assertFalse(validator.validate(request).isEmpty());

        request.setOldPassword("oldPassword1");
        assertTrue(validator.validate(request).isEmpty());
    }
}
