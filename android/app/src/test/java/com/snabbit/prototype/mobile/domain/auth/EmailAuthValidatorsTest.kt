package com.snabbit.prototype.mobile.domain.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailAuthValidatorsTest {
    @Test fun `email validation normalizes and rejects malformed or oversized values`() {
        assertTrue(EmailValidator.isValid(" Person@Example.Invalid "))
        assertFalse(EmailValidator.isValid("missing-domain"))
        assertFalse(EmailValidator.isValid("a@${"x".repeat(250)}.invalid"))
    }

    @Test fun `name validation trims spaces and enforces backend bounds`() {
        assertTrue(NameValidator.isValid("  Test   Customer  "))
        assertFalse(NameValidator.isValid("x"))
        assertFalse(NameValidator.isValid("x".repeat(121)))
    }

    @Test fun `password rules report every documented local rule`() {
        val common = listOf("pass", "word").joinToString("")
        val commonHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(common.toByteArray()).joinToString("") { "%02x".format(it) }
        val rules = PasswordRules(setOf(commonHash))
        assertFalse(rules.validate("short").passes(PasswordRule.LENGTH))
        assertFalse(rules.validate("person@example.invalid", "person@example.invalid").passes(PasswordRule.DIFFERS_FROM_EMAIL))
        assertFalse(rules.validate("Test Customer", name = "Test Customer").passes(PasswordRule.DIFFERS_FROM_NAME))
        assertFalse(rules.validate(common).passes(PasswordRule.NOT_COMMON))
        assertTrue(rules.validate("Synthetic!Credential-2042", "person@example.invalid", "Test Customer").isValid)
    }
}

