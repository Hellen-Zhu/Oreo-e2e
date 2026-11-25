package com.example.module.oreo.testdata;

import com.example.module.oreo.utils.DateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Values of the test data that are filled in when the case runs, in UTC whatever this machine's time zone:
 * <ul>
 *   <li>{@code ${date:T}}, {@code ${date:T+N}}, {@code ${date:T-N}}: the date plus or minus N calendar days,
 *       {@code yyyy-MM-dd} ({@link DateUtil#resolveDate});</li>
 *   <li>{@code ${datetime:T}}, {@code ${datetime:T+N}}, {@code ${datetime:T-N}}: the time, to the second, plus or
 *       minus N days; {@code ${datetime:T+1 09:00}} ({@code HH:mm} or {@code HH:mm:ss}): that UTC time on that date;
 *       {@code yyyy-MM-dd'T'HH:mm:ss'Z'} ({@link DateUtil#resolveDateTime}).</li>
 * </ul>
 * {@code T} is the moment {@link #fixNow} fixed, the start of the scenario, so every read of a scenario's data gives
 * the same values; the moment of the read when none is fixed (the offline tool). A placeholder can be the whole value
 * or a part of it. Every other {@code ${...}} is left as it is: a step fills it in, or json-unit reads it.
 */
final class DataPlaceholders {

    private static final String DATE_START = "${date:";
    private static final String DATE_TIME_START = "${datetime:";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(date|datetime):([^}]*)}");
    private static final Pattern DATE = Pattern.compile("[Tt]([+-]\\d{1,5})?");
    private static final Pattern DATE_TIME =
            Pattern.compile("[Tt]([+-]\\d{1,5})?( ([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?)?");
    private static final String FORMS = "write ${date:T}, ${date:T+N}, ${date:T-N} (N calendar days),"
            + " or ${datetime:...} with the same T, optionally followed by a UTC time (${datetime:T+1 09:00})";

    private static volatile Instant fixedNow;

    private DataPlaceholders() {}

    /** Fixes the moment {@code T} stands for until the next call; {@code null}: the moment of each read. */
    static void fixNow(Instant now) {
        fixedNow = now;
    }

    /** A copy of {@code node} with every date and date-time placeholder replaced. */
    static JsonNode resolve(JsonNode node) {
        Instant fixed = fixedNow;
        return resolve(node, fixed != null ? fixed : DateUtil.now());
    }

    private static JsonNode resolve(JsonNode node, Instant now) {
        if (node.isTextual()) {
            String text = node.textValue();
            return hasPlaceholder(text) ? TextNode.valueOf(resolveText(text, now)) : node;
        }
        if (node.isObject()) {
            ObjectNode copy = Json.MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                copy.set(field.getKey(), resolve(field.getValue(), now));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = Json.MAPPER.createArrayNode();
            for (JsonNode element : node) {
                copy.add(resolve(element, now));
            }
            return copy;
        }
        return node;
    }

    /**
     * Fails with an {@link IllegalArgumentException} naming the path (relative to {@code node}) of the first date or
     * date-time placeholder that is none of the forms above.
     */
    static void check(JsonNode node) {
        check(node, "");
    }

    private static void check(JsonNode node, String at) {
        if (node.isTextual()) {
            String text = node.textValue();
            if (!hasPlaceholder(text)) return;
            Matcher m = PLACEHOLDER.matcher(text);
            while (m.find()) {
                if (!valid(m)) {
                    throw new IllegalArgumentException(where(at) + ": '" + m.group() + "' is not a " + kind(m)
                            + "; " + FORMS);
                }
            }
            if (hasPlaceholder(m.replaceAll(""))) {
                throw new IllegalArgumentException(where(at) + ": a ${date:... or ${datetime:... without its closing }");
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                check(field.getValue(), at.isEmpty() ? field.getKey() : at + "." + field.getKey());
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                check(node.get(i), at + "[" + i + "]");
            }
        }
    }

    private static String resolveText(String text, Instant now) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            if (!valid(m)) {
                throw new IllegalStateException("[TestData] '" + m.group() + "' is not a " + kind(m) + "; " + FORMS);
            }
            String value = isDate(m)
                    ? DateUtil.resolveDate(m.group(2), now)
                    : DateUtil.resolveDateTime(m.group(2), now);
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean hasPlaceholder(String text) {
        return text.contains(DATE_START) || text.contains(DATE_TIME_START);
    }

    private static boolean isDate(Matcher m) {
        return "date".equals(m.group(1));
    }

    private static boolean valid(Matcher m) {
        return (isDate(m) ? DATE : DATE_TIME).matcher(m.group(2)).matches();
    }

    private static String kind(Matcher m) {
        return isDate(m) ? "date" : "date and time";
    }

    private static String where(String at) {
        return at.isEmpty() ? "(top level)" : at;
    }
}
