package com.example.module.oreo.testdata;

import com.example.module.oreo.utils.DateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Values of the test data that are filled in when the case runs: {@code ${date:T}}, {@code ${date:T+N}} and
 * {@code ${date:T-N}} in a string value become today's date plus or minus N calendar days, {@code yyyy-MM-dd} in
 * this machine's time zone ({@link DateUtil#resolveDate}). {@code ${date:S}}, {@code ${date:S+N}} and
 * {@code ${date:S-N}} count from the final settlement date of the case's product instead
 * ({@link ProductDatFiles#settlementDate()}): a date that stays before it however long the suite runs. The placeholder
 * can be the whole value or a part of it. Every other {@code ${...}} is left as it is: a step fills it in, or
 * json-unit reads it.
 */
final class DataPlaceholders {

    private static final String DATE_START = "${date:";
    private static final Pattern DATE = Pattern.compile("\\$\\{date:([^}]*)}");
    private static final Pattern EXPRESSION = Pattern.compile("([TtSs])([+-]\\d{1,5})?");

    private DataPlaceholders() {}

    /** A copy of {@code node} with every date placeholder replaced; data without a product ({@code S} fails). */
    static JsonNode resolve(JsonNode node) {
        return resolve(node, null, "data without a product");
    }

    /**
     * A copy of {@code node} with every date placeholder replaced by the date of today's run; {@code S} is
     * {@code settlement}, the final settlement date of {@code whose} (the case, for the message when it has none).
     */
    static JsonNode resolve(JsonNode node, LocalDate settlement, String whose) {
        if (node.isTextual()) {
            String text = node.textValue();
            return text.contains(DATE_START) ? TextNode.valueOf(resolveText(text, settlement, whose)) : node;
        }
        if (node.isObject()) {
            ObjectNode copy = Json.MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                copy.set(field.getKey(), resolve(field.getValue(), settlement, whose));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = Json.MAPPER.createArrayNode();
            for (JsonNode element : node) {
                copy.add(resolve(element, settlement, whose));
            }
            return copy;
        }
        return node;
    }

    /**
     * Fails with an {@link IllegalArgumentException} naming the path (relative to {@code node}) of the first date
     * placeholder that is not {@code ${date:T}}, {@code ${date:T+N}}, {@code ${date:T-N}} or the same with {@code S}.
     */
    static void check(JsonNode node) {
        check(node, "");
    }

    private static void check(JsonNode node, String at) {
        if (node.isTextual()) {
            String text = node.textValue();
            if (!text.contains(DATE_START)) return;
            Matcher m = DATE.matcher(text);
            while (m.find()) {
                if (!EXPRESSION.matcher(m.group(1)).matches()) {
                    throw new IllegalArgumentException(where(at) + ": '" + m.group() + "' is not a date;"
                            + " write ${date:T}, ${date:T+N} or ${date:T-N} (N calendar days)");
                }
            }
            if (m.replaceAll("").contains(DATE_START)) {
                throw new IllegalArgumentException(where(at) + ": a ${date:... without its closing }");
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

    private static String resolveText(String text, LocalDate settlement, String whose) {
        Matcher m = DATE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String expression = m.group(1);
            Matcher e = EXPRESSION.matcher(expression);
            if (!e.matches()) {
                throw new IllegalStateException("[TestData] '" + m.group() + "' is not a date;"
                        + " write ${date:T}, ${date:T+N} or ${date:T-N}, or the same with S");
            }
            String date;
            if (e.group(1).equalsIgnoreCase("S")) {
                if (settlement == null) {
                    throw new IllegalStateException("[TestData] '" + m.group() + "' in " + whose + ": S is the final"
                            + " settlement date of the case's product, and it has none; give it in ProductDatFiles");
                }
                date = (e.group(2) == null ? settlement : settlement.plusDays(Long.parseLong(e.group(2)))).toString();
            } else {
                date = DateUtil.resolveDate(expression);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(date));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String where(String at) {
        return at.isEmpty() ? "(top level)" : at;
    }
}
