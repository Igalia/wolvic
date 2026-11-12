package com.igalia.wolvic.telemetry;

import java.util.List;
import java.util.regex.Pattern;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

// Crash reports carry the exception message and stack trace, which may incidentally contain the
// address of a page. This removes anything that looks like a URL before the record is stored or sent.
final class URLRemoverLogRecordProcessor implements LogRecordProcessor {
    private static final List<AttributeKey<String>> FREE_TEXT_KEYS = List.of(
            AttributeKey.stringKey("exception.message"),
            AttributeKey.stringKey("exception.stacktrace"));

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:[a-z][a-z0-9+.-]*://|www\\.|(?:about|blob|data|javascript|mailto|resource|file):)[^\\s\"'<>]+");

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
        for (AttributeKey<String> key : FREE_TEXT_KEYS) {
            final String value = logRecord.getAttribute(key);
            if (value == null) {
                continue;
            }
            final String redacted = URL.matcher(value).replaceAll("<url>");
            if (!redacted.equals(value)) {
                logRecord.setAttribute(key, redacted);
            }
        }
    }
}
