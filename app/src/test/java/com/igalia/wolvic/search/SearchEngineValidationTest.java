package com.igalia.wolvic.search;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class SearchEngineValidationTest {

    @Test
    public void testValidSearchUrl() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(RuntimeEnvironment.application, "https://example.com/search?q=%s");
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    public void testMissingSearchUrl() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(RuntimeEnvironment.application, null);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("required"));
    }

    @Test
    public void testEmptySearchUrl() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(RuntimeEnvironment.application, "");
        assertFalse(result.isValid());
    }

    @Test
    public void testMissingPlaceholder() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(RuntimeEnvironment.application, "https://example.com/search");
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("%s"));
    }

    @Test
    public void testHttpUrlRejected() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(RuntimeEnvironment.application, "http://example.com/search?q=%s");
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("http"));
    }

    @Test
    public void testInvalidScheme() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(RuntimeEnvironment.application, "ftp://example.com/search?q=%s");
        assertFalse(result.isValid());
    }

    @Test
    public void testLocalhostRejected() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(RuntimeEnvironment.application, "https://localhost/search?q=%s");
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Private"));
    }

    @Test
    public void testValidName() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateName(RuntimeEnvironment.application, "My Search Engine");
        assertTrue(result.isValid());
    }

    @Test
    public void testEmptyNameRejected() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateName(RuntimeEnvironment.application, "");
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("required"));
    }

    @Test
    public void testNameTooLongRejected() {
        String longName = "A".repeat(100);
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateName(RuntimeEnvironment.application, longName);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("50"));
    }

    @Test
    public void testValidSuggestUrl() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSuggestUrl(RuntimeEnvironment.application, "https://example.com/suggest?q=%s");
        assertTrue(result.isValid());
    }

    @Test
    public void testEmptySuggestUrlIsValid() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSuggestUrl(RuntimeEnvironment.application, "");
        assertTrue(result.isValid());
    }

    @Test
    public void testNullSuggestUrlIsValid() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSuggestUrl(RuntimeEnvironment.application, null);
        assertTrue(result.isValid());
    }

    @Test
    public void testSuggestUrlWithoutPlaceholderRejected() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSuggestUrl(RuntimeEnvironment.application, "https://example.com/suggest");
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("%s"));
    }

    @Test
    public void testValidateAllReturnsAllErrors() {
        List<SearchEngineValidation.ValidationResult> results = 
                SearchEngineValidation.validateAll(RuntimeEnvironment.application, "", "", null);
        
        assertTrue(results.size() >= 2);
    }
}
