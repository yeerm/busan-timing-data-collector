package com.busantiming.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContentTypeValidatorTest {

    private ContentTypeValidator validator;

    @BeforeEach
    void setUp() {
        Set<Integer> validIds = Set.of(12, 14, 15, 25, 28, 32, 38, 39);
        validator = new ContentTypeValidator(validIds);
    }

    @Test
    void parseAndValidate_validContentTypeId() {
        assertEquals(12, validator.parseAndValidate("12"));
        assertEquals(39, validator.parseAndValidate("39"));
    }

    @Test
    void parseAndValidate_nullReturnsNull() {
        assertNull(validator.parseAndValidate(null));
    }

    @Test
    void parseAndValidate_emptyStringReturnsNull() {
        assertNull(validator.parseAndValidate(""));
        assertNull(validator.parseAndValidate("  "));
    }

    @Test
    void parseAndValidate_invalidNumberReturnsNull() {
        assertNull(validator.parseAndValidate("99"));
        assertNull(validator.parseAndValidate("0"));
    }

    @Test
    void parseAndValidate_nonNumericReturnsNull() {
        assertNull(validator.parseAndValidate("abc"));
        assertNull(validator.parseAndValidate("12a"));
    }

    @Test
    void isValid_checksCorrectly() {
        assertTrue(validator.isValid("12"));
        assertTrue(validator.isValid("38"));
        assertFalse(validator.isValid("99"));
        assertFalse(validator.isValid(null));
        assertFalse(validator.isValid("abc"));
    }
}
