package com.igalia.wolvic;

import static org.junit.Assert.assertEquals;

import com.igalia.wolvic.utils.StringUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = TestApplication.class)
public class StringUtilsTest {
    // Version keys from gh-pages-1.10 at 1de65c8a2b5319c24c592341e08fe6fb49e05d61,
    // in the expected newest-first order.
    private static final List<String> PROPS_VERSIONS = Arrays.asList(
            "1.10", "1.9", "1.8.3", "1.8.2", "1.8.1", "1.8.0", "1.7.1", "1.7.0",
            "1.6.1", "1.6.0", "1.5.2", "1.5.1", "1.5", "1.4.2", "1.4.1", "1.4",
            "1.3.4", "1.3.3", "1.3.2", "1.3.1", "1.3", "1.2", "1.1.1", "1.1",
            "1.0.2", "1.0.1", "1.0", "0.9.6", "0.9.5");
    private static final List<String> PROPS_CHROMIUM_VERSIONS = Arrays.asList(
            "1.3", "1.2.3", "1.2.2", "1.2.1", "1.2", "1.1", "1.0");

    @Test
    public void versionNamesSortNewestFirst() {
        for (List<String> published : Arrays.asList(PROPS_VERSIONS, PROPS_CHROMIUM_VERSIONS)) {
            assertVersionsSortNewestFirst(published);
            List<String> extended = new ArrayList<>(Arrays.asList(
                    "2.0", "1.11", "1.10.10", "1.10.2", "1.10.1", "1.10"));
            for (String version : published) {
                if (!extended.contains(version)) {
                    extended.add(version);
                }
            }
            assertVersionsSortNewestFirst(extended);
        }
        assertVersionsSortNewestFirst(Arrays.asList(
                "10.0", "2.0", "1.9.10", "1.9.2", "1.0.0.1", "1.0", "0",
                "junk", "1.10-beta"));
        assertEquals(0, StringUtils.compareVersionNamesNewestFirst("01.010", "1.10.0.0"));
    }

    private static void assertVersionsSortNewestFirst(List<String> expected) {
        List<String> versions = new ArrayList<>(expected);
        Collections.shuffle(versions, new Random(0));
        assertEquals(expected, versions.stream().sorted(StringUtils::compareVersionNamesNewestFirst)
                .collect(Collectors.toList()));
    }
}
