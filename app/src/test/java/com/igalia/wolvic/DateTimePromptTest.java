package com.igalia.wolvic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.igalia.wolvic.browser.api.WSession;
import com.igalia.wolvic.ui.widgets.prompts.DateTimePromptWidget;

import org.junit.Test;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Tests for DateTime picker step and list attribute helpers.
 */
public class DateTimePromptTest {

    // Reflection helpers - the methods under test are private static.

    private double invokeParseStep(String input) throws Exception {
        Method m = DateTimePromptWidget.class.getDeclaredMethod("parseStep", String.class);
        m.setAccessible(true);
        return (double) m.invoke(null, input);
    }

    private Date invokeSnapToStep(Date selected, double step, Date min, int type) throws Exception {
        Method m = DateTimePromptWidget.class.getDeclaredMethod(
                "snapToStep", Date.class, double.class, Date.class, int.class);
        m.setAccessible(true);
        return (Date) m.invoke(null, selected, step, min, type);
    }

    @SuppressWarnings("unchecked")
    private List<Date> invokeBuildSuggestions(SimpleDateFormat fmt, String[] values) throws Exception {
        Method m = DateTimePromptWidget.class.getDeclaredMethod(
                "buildSuggestions", SimpleDateFormat.class, String[].class);
        m.setAccessible(true);
        return (List<Date>) m.invoke(null, fmt, values);
    }

    private SimpleDateFormat utcFmt(String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    }

    // parseStep

    @Test
    public void parseStep_null_isNaN() throws Exception {
        assertTrue(Double.isNaN(invokeParseStep(null)));
    }

    @Test
    public void parseStep_empty_isNaN() throws Exception {
        assertTrue(Double.isNaN(invokeParseStep("")));
    }

    @Test
    public void parseStep_any_isNaN() throws Exception {
        assertTrue(Double.isNaN(invokeParseStep("any")));
    }

    @Test
    public void parseStep_anyMixedCase_isNaN() throws Exception {
        assertTrue(Double.isNaN(invokeParseStep("ANY")));
    }

    @Test
    public void parseStep_garbage_isNaN() throws Exception {
        assertTrue(Double.isNaN(invokeParseStep("not-a-number")));
    }

    @Test
    public void parseStep_integer_returnsValue() throws Exception {
        assertEquals(2.0, invokeParseStep("2"), 0.0);
    }

    @Test
    public void parseStep_decimal_returnsValue() throws Exception {
        assertEquals(0.5, invokeParseStep("0.5"), 0.0);
    }

    @Test
    public void parseStep_largeSeconds_returnsValue() throws Exception {
        assertEquals(3600.0, invokeParseStep("3600"), 0.0);
    }

    // snapToStep - DATE type (step unit = days)

    @Test
    public void snapToStep_date_exactMultiple_unchanged() throws Exception {
        SimpleDateFormat fmt = utcFmt("yyyy-MM-dd");
        Date min      = fmt.parse("2024-01-01");
        Date selected = fmt.parse("2024-01-05"); // 4 days after min, step=2 -> n=2 exactly
        Date result   = invokeSnapToStep(selected, 2.0, min,
                WSession.PromptDelegate.DateTimePrompt.Type.DATE);
        assertEquals("2024-01-05", fmt.format(result));
    }

    @Test
    public void snapToStep_date_roundsDown() throws Exception {
        // Jan 4 is 3 days after min, rounds down to Jan 1 with step=7
        SimpleDateFormat fmt = utcFmt("yyyy-MM-dd");
        Date min      = fmt.parse("2024-01-01");
        Date selected = fmt.parse("2024-01-04");
        Date result   = invokeSnapToStep(selected, 7.0, min,
                WSession.PromptDelegate.DateTimePrompt.Type.DATE);
        assertEquals("2024-01-01", fmt.format(result));
    }

    @Test
    public void snapToStep_date_roundsUp() throws Exception {
        // Jan 6 is 5 days after min, closer to Jan 8 (step=7)
        SimpleDateFormat fmt = utcFmt("yyyy-MM-dd");
        Date min      = fmt.parse("2024-01-01");
        Date selected = fmt.parse("2024-01-06");
        Date result   = invokeSnapToStep(selected, 7.0, min,
                WSession.PromptDelegate.DateTimePrompt.Type.DATE);
        assertEquals("2024-01-08", fmt.format(result));
    }

    @Test
    public void snapToStep_date_noMin_usesEpoch() throws Exception {
        SimpleDateFormat fmt = utcFmt("yyyy-MM-dd");
        Date selected = fmt.parse("1970-01-03"); // 2 days from epoch, step=2 -> n=1 exact
        Date result   = invokeSnapToStep(selected, 2.0, null,
                WSession.PromptDelegate.DateTimePrompt.Type.DATE);
        assertEquals("1970-01-03", fmt.format(result));
    }

    // snapToStep - WEEK type (step unit = weeks, so step=1 = 7 days)

    @Test
    public void snapToStep_week_snapsToWeekBoundary() throws Exception {
        // step=2 weeks from Jan 1.  Jan 10 is 9 days in -> closer to 14 (2 wks) than 0
        SimpleDateFormat fmt = utcFmt("yyyy-MM-dd");
        Date min      = fmt.parse("2024-01-01");
        Date selected = fmt.parse("2024-01-10");
        Date result   = invokeSnapToStep(selected, 2.0, min,
                WSession.PromptDelegate.DateTimePrompt.Type.WEEK);
        assertEquals("2024-01-15", fmt.format(result));
    }

    // snapToStep - MONTH type (skipped, returns selected unchanged)

    @Test
    public void snapToStep_month_skipped() throws Exception {
        SimpleDateFormat fmt = utcFmt("yyyy-MM-dd");
        Date selected = fmt.parse("2024-03-17");
        Date result   = invokeSnapToStep(selected, 2.0, fmt.parse("2024-01-01"),
                WSession.PromptDelegate.DateTimePrompt.Type.MONTH);
        assertEquals("2024-03-17", fmt.format(result));
    }

    // snapToStep - TIME type (step unit = seconds)

    @Test
    public void snapToStep_time_snapsToNearest15Min() throws Exception {
        // step=900s (15 min). 00:17 -> snaps to 00:15
        SimpleDateFormat fmt = utcFmt("HH:mm");
        Date selected = fmt.parse("00:17");
        Date result   = invokeSnapToStep(selected, 900.0, null,
                WSession.PromptDelegate.DateTimePrompt.Type.TIME);
        assertEquals("00:15", fmt.format(result));
    }

    @Test
    public void snapToStep_time_exactBoundary_unchanged() throws Exception {
        SimpleDateFormat fmt = utcFmt("HH:mm");
        Date selected = fmt.parse("01:30"); // 5400s / 900 = 6, exact
        Date result   = invokeSnapToStep(selected, 900.0, null,
                WSession.PromptDelegate.DateTimePrompt.Type.TIME);
        assertEquals("01:30", fmt.format(result));
    }

    @Test
    public void snapToStep_time_withMin_snapsRelativeToMin() throws Exception {
        // min=00:05, step=900s, 00:22 is 1020s after min -> closer to 900 than 1800
        SimpleDateFormat fmt = utcFmt("HH:mm");
        Date min      = fmt.parse("00:05");
        Date selected = fmt.parse("00:22");
        Date result   = invokeSnapToStep(selected, 900.0, min,
                WSession.PromptDelegate.DateTimePrompt.Type.TIME);
        assertEquals("00:20", fmt.format(result));
    }

    // buildSuggestions

    @Test
    public void buildSuggestions_null_returnsEmpty() throws Exception {
        List<Date> result = invokeBuildSuggestions(utcFmt("yyyy-MM-dd"), null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void buildSuggestions_emptyArray_returnsEmpty() throws Exception {
        List<Date> result = invokeBuildSuggestions(utcFmt("yyyy-MM-dd"), new String[0]);
        assertTrue(result.isEmpty());
    }

    @Test
    public void buildSuggestions_validDates_allParsed() throws Exception {
        SimpleDateFormat fmt = utcFmt("yyyy-MM-dd");
        List<Date> result = invokeBuildSuggestions(fmt,
                new String[]{"2024-03-15", "2024-06-01", "2024-12-25"});
        assertEquals(3, result.size());
        assertEquals("2024-03-15", fmt.format(result.get(0)));
        assertEquals("2024-06-01", fmt.format(result.get(1)));
        assertEquals("2024-12-25", fmt.format(result.get(2)));
    }

    @Test
    public void buildSuggestions_invalidEntriesSkipped() throws Exception {
        SimpleDateFormat fmt = utcFmt("yyyy-MM-dd");
        List<Date> result = invokeBuildSuggestions(fmt,
                new String[]{"2024-03-15", "not-a-date", "2024-06-01", "", null});
        assertEquals(2, result.size());
        assertEquals("2024-03-15", fmt.format(result.get(0)));
        assertEquals("2024-06-01", fmt.format(result.get(1)));
    }

    @Test
    public void buildSuggestions_timeFormat_parsedCorrectly() throws Exception {
        SimpleDateFormat fmt = utcFmt("HH:mm");
        List<Date> result = invokeBuildSuggestions(fmt,
                new String[]{"09:00", "12:30", "18:45"});
        assertEquals(3, result.size());
        assertEquals("09:00", fmt.format(result.get(0)));
        assertEquals("12:30", fmt.format(result.get(1)));
        assertEquals("18:45", fmt.format(result.get(2)));
    }

    @Test
    public void buildSuggestions_allInvalid_returnsEmpty() throws Exception {
        List<Date> result = invokeBuildSuggestions(utcFmt("yyyy-MM-dd"),
                new String[]{"bad", "", null, "also-bad"});
        assertTrue(result.isEmpty());
    }
}
