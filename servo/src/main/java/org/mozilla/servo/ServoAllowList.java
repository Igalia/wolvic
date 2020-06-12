package org.mozilla.servo;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ServoAllowList {
    private final Pattern[] mRules;

    public ServoAllowList(Context context) {
        Resources res = context.getResources();
        Stream<String> rules = Stream.of(res.getStringArray(R.array.servo_white_list));
        mRules = rules.map(Pattern::compile).toArray(Pattern[]::new);
    }

    public boolean isAllowed(String url) {
        return url != null && Stream.of(mRules).anyMatch(r -> r.matcher(url).matches());
    }
}
