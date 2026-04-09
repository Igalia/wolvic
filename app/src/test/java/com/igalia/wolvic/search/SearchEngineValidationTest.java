package com.igalia.wolvic.search;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.*;

import androidx.test.core.app.ApplicationProvider;

import com.igalia.wolvic.R;

@RunWith(RobolectricTestRunner.class)
public class SearchEngineValidationTest {

    @Test
    public void testValidSearchUrl() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(ApplicationProvider.getApplicationContext(), "https://example.com/search?q=%s");
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    public void testHttpSearchUrl() {
        SearchEngineValidation.ValidationResult result =
                SearchEngineValidation.validateSearchUrl(ApplicationProvider.getApplicationContext(), "http://example.com/search?q=%s");
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    public void testMissingSearchUrl() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(ApplicationProvider.getApplicationContext(), null);
        assertFalse(result.isValid());
        assertEquals(result.getErrorMessage(), ApplicationProvider.getApplicationContext().getString(R.string.search_engine_error_url_required));
    }

    @Test
    public void testEmptySearchUrl() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(ApplicationProvider.getApplicationContext(), "");
        assertFalse(result.isValid());
    }

    @Test
    public void testMissingPlaceholder() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(ApplicationProvider.getApplicationContext(), "https://example.com/search");
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("%s"));
    }

    @Test
    public void testInvalidScheme() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(ApplicationProvider.getApplicationContext(), "ftp://example.com/search?q=%s");
        assertFalse(result.isValid());
    }

    @Test
    public void testLocalhostRejected() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSearchUrl(ApplicationProvider.getApplicationContext(), "https://localhost/search?q=%s");
        assertFalse(result.isValid());
        assertEquals(result.getErrorMessage(), ApplicationProvider.getApplicationContext().getString(R.string.search_engine_error_url_private));
    }

    @Test
    public void testValidName() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateName(ApplicationProvider.getApplicationContext(), "My Search Engine");
        assertTrue(result.isValid());
    }

    @Test
    public void testEmptyNameRejected() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateName(ApplicationProvider.getApplicationContext(), "");
        assertFalse(result.isValid());
        assertEquals(result.getErrorMessage(), ApplicationProvider.getApplicationContext().getString(R.string.search_engine_error_name_required));
    }

    @Test
    public void testNameTooLongRejected() {
        String longName = "A".repeat(100);
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateName(ApplicationProvider.getApplicationContext(), longName);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("50"));
    }

    @Test
    public void testValidSuggestUrl() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSuggestUrl(ApplicationProvider.getApplicationContext(), "https://example.com/suggest?q=%s");
        assertTrue(result.isValid());
    }

    @Test
    public void testEmptySuggestUrlIsValid() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSuggestUrl(ApplicationProvider.getApplicationContext(), "");
        assertTrue(result.isValid());
    }

    @Test
    public void testNullSuggestUrlIsValid() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSuggestUrl(ApplicationProvider.getApplicationContext(), null);
        assertTrue(result.isValid());
    }

    @Test
    public void testSuggestUrlWithoutPlaceholderRejected() {
        SearchEngineValidation.ValidationResult result = 
                SearchEngineValidation.validateSuggestUrl(ApplicationProvider.getApplicationContext(), "https://example.com/suggest");
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("%s"));
    }

    @Test
    public void testValidateAllReturnsAllErrors() {
        List<SearchEngineValidation.ValidationResult> results = 
                SearchEngineValidation.validateAll(ApplicationProvider.getApplicationContext(), "", "", null);
        
        assertTrue(results.size() >= 2);
    }
}
