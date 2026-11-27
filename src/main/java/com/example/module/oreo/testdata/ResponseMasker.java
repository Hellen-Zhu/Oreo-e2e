package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns an actual API response into the expected file: the fields named by {@code dynamicFields} are replaced
 * by JsonUnit placeholders, everything else is kept exactly (numbers digit for digit).
 *
 * <table>
 *   <caption>Rules</caption>
 *   <tr><td>{@code type}</td><td>{@code ${json-unit.any-string|any-number|any-boolean}}; an array
 *       {@code ${json-unit.any-array}} (any length and elements, JsonUnit 4); {@code null} stays; an object fails
 *       (JsonUnit has no any-object: {@code type-tree} or {@code ignore})</td></tr>
 *   <tr><td>{@code type-tree}</td><td>the subtree, every leaf replaced as by {@code type}</td></tr>
 *   <tr><td>{@code regex:<p>}</td><td>{@code ${json-unit.regex}<p>}; the actual value must match</td></tr>
 *   <tr><td>{@code ignore}</td><td>{@code ${json-unit.ignore}}</td></tr>
 *   <tr><td>{@code var:<name>}</td><td>{@code ${var:<name>}}; the actual value must be the scenario variable's
 *       value, put in again by {@link #fillVariables} before the comparison</td></tr>
 *   <tr><td>{@code request:<path>}</td><td>{@code ${request:<path>}}; the actual value must be the value the request of
 *       the run has at {@code <path>} (a {@code ${date:T+1}} as it was resolved), put in again by
 *       {@link #fillVariables}; see {@link #requestValue}</td></tr>
 * </table>
 *
 * A path is keys joined by {@code .}, with {@code [n]} for one array element and {@code [*]} for every element;
 * {@code *} as a key is every value of an object, {@code data.fixings.*} (also for keys a path cannot name, such as
 * {@code FX:EURSGD@[BFIX]-1500-JP TYO}).
 */
public final class ResponseMasker {

    public static final String ANY_STRING = "${json-unit.any-string}";
    public static final String ANY_NUMBER = "${json-unit.any-number}";
    public static final String ANY_BOOLEAN = "${json-unit.any-boolean}";
    public static final String ANY_ARRAY = "${json-unit.any-array}";
    public static final String REGEX = "${json-unit.regex}";
    public static final String IGNORE = "${json-unit.ignore}";
    public static final String VAR_START = "${var:";
    public static final String REQUEST_START = "${request:";
    /** The name under which the lookup of {@link #mask} and {@link #fillVariables} gives a request value. */
    public static final String REQUEST_PREFIX = "request:";

    private static final Pattern PLAIN_KEY = Pattern.compile("[^.\\[\\]]+");
    private static final Pattern VAR_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");
    private static final Pattern VAR = Pattern.compile("\\$\\{(?:var:(" + VAR_NAME.pattern() + ")|request:([^}]+))}");
    private static final Object ANY = new Object();
    private static final Object ANY_KEY = new Object();

    private ResponseMasker() {}

    /** {@link #mask(String, Map, Function, String)} without scenario variables: a {@code var:} or {@code request:} rule fails. */
    public static String mask(String actualJson, Map<String, String> rules, String caseLabel) {
        return mask(actualJson, rules, name -> null, caseLabel);
    }

    /**
     * The pretty-printed expected file for {@code actualJson}; fails on a rule that does not fit the response.
     * {@code variables} gives the value of a scenario variable by name, {@code null} when it is not stored, and the
     * value of the run's request at a path by {@link #REQUEST_PREFIX} + path.
     */
    public static String mask(String actualJson, Map<String, String> rules, Function<String, String> variables,
                              String caseLabel) {
        JsonNode actual;
        try {
            actual = Json.parse(actualJson);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("[ResponseMasker] the response of " + caseLabel + " is not JSON: "
                    + e.getMessage(), e);
        }
        JsonNode masked = actual.deepCopy();
        if (rules != null) {
            for (Map.Entry<String, String> rule : rules.entrySet()) {
                masked = apply(actual, masked, rule.getKey(), rule.getValue(), variables, caseLabel);
            }
        }
        return Json.pretty(masked);
    }

    private static JsonNode apply(JsonNode actual, JsonNode masked, String path, String rule,
                                  Function<String, String> variables, String caseLabel) {
        List<Object> steps = parsePath(path, caseLabel);
        List<List<Object>> targets = new ArrayList<>();
        collect(actual, steps, 0, new ArrayList<>(), targets);
        if (targets.isEmpty()) {
            throw new IllegalStateException("[ResponseMasker] rule " + path + " matches nothing in the response of "
                    + caseLabel + " (a typo, or a rule this case should not inherit: set it to null for the case)");
        }
        for (List<Object> target : targets) {
            JsonNode value = at(actual, target);
            JsonNode replacement = replacement(value, path, rule, variables, caseLabel);
            masked = set(masked, target, replacement);
        }
        return masked;
    }

    private static JsonNode replacement(JsonNode value, String path, String rule,
                                        Function<String, String> variables, String caseLabel) {
        String where = "rule " + path + " = " + rule + " in the response of " + caseLabel;
        if (rule == null) {
            throw new IllegalStateException("[ResponseMasker] " + where + ": a rule is type, type-tree, regex:<pattern>,"
                    + " var:<name>, request:<path> or ignore");
        }
        if (rule.equals("type")) {
            if (value.isArray()) {
                return TextNode.valueOf(ANY_ARRAY);
            }
            if (value.isObject()) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": the value is an object, which"
                        + " JsonUnit has no type placeholder for; use type-tree (its structure is checked) or ignore");
            }
            return leaf(value, where);
        }
        if (rule.equals("type-tree")) {
            return tree(value, where);
        }
        if (rule.equals("ignore")) {
            return TextNode.valueOf(IGNORE);
        }
        if (rule.startsWith("regex:")) {
            String pattern = rule.substring("regex:".length());
            if (!value.isTextual()) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": regex applies to text, the value is "
                        + Json.compact(value));
            }
            Matcher matcher;
            try {
                matcher = Pattern.compile(pattern).matcher(value.asText());
            } catch (PatternSyntaxException e) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": invalid pattern: " + e.getDescription(), e);
            }
            if (!matcher.matches()) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": the actual value "
                        + Json.compact(value) + " does not match; not recorded");
            }
            return TextNode.valueOf(REGEX + pattern);
        }
        if (rule.startsWith("var:")) {
            String name = rule.substring("var:".length());
            if (!VAR_NAME.matcher(name).matches()) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": a variable name is a letter, then"
                        + " letters, digits, '_', '.' or '-'");
            }
            if (value.isContainerNode() || value.isNull()) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": var applies to text, a number or a"
                        + " boolean, the value is " + Json.compact(value));
            }
            String expected = variables.apply(name);
            if (expected == null) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": the scenario has no variable " + name
                        + "; not recorded");
            }
            if (!value.asText().equals(expected)) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": the actual value "
                        + Json.compact(value) + " is not the value of " + name + " (" + expected + "); not recorded");
            }
            return TextNode.valueOf(VAR_START + name + "}");
        }
        if (rule.startsWith(REQUEST_PREFIX)) {
            String requestPath = rule.substring(REQUEST_PREFIX.length());
            checkRequestPath(requestPath, caseLabel);
            if (value.isContainerNode() || value.isNull()) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": request applies to text, a number or"
                        + " a boolean, the value is " + Json.compact(value));
            }
            String expected = variables.apply(REQUEST_PREFIX + requestPath);
            if (expected == null) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": the request has no value at "
                        + requestPath + "; not recorded");
            }
            if (!sameValue(value, expected)) {
                throw new IllegalStateException("[ResponseMasker] " + where + ": the actual value "
                        + Json.compact(value) + " is not the request's " + requestPath + " (" + expected
                        + "); not recorded");
            }
            return TextNode.valueOf(REQUEST_START + requestPath + "}");
        }
        throw new IllegalStateException("[ResponseMasker] " + where + ": unknown rule; a rule is type, type-tree,"
                + " regex:<pattern>, var:<name>, request:<path> or ignore");
    }

    /** The same text, or the same number ({@code 35000} = {@code "35000.00"}). */
    private static boolean sameValue(JsonNode actual, String expected) {
        if (actual.asText().equals(expected)) return true;
        if (!actual.isNumber() && !actual.isTextual()) return false;
        try {
            return new BigDecimal(actual.asText()).compareTo(new BigDecimal(expected)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Fails unless {@code path} names one value of a request: keys and {@code [n]}, no {@code *} or {@code [*]}, and
     * not a shared part ({@code {basic}} is a response path).
     */
    public static void checkRequestPath(String path, String caseLabel) {
        if (path.startsWith("{")) {
            throw new IllegalStateException("[ResponseMasker] request path '" + path + "' of " + caseLabel
                    + " starts at a shared part; a request path starts at the request body (basic.dealDate,"
                    + " data.settleDate)");
        }
        for (Object step : parsePath(path, caseLabel)) {
            if (isAny(step) || isAnyKey(step)) {
                throw new IllegalStateException("[ResponseMasker] request path '" + path + "' of " + caseLabel
                        + " names several values; it names one (no * or [*])");
            }
        }
    }

    /**
     * The value of {@code requestJson} at {@code path} as text, {@code null} when it has none. A key on an array
     * selects the element whose {@code key} is that key and takes its {@code value}: {@code data.settleDate} is the
     * {@code value} of the entry {@code {"key": "settleDate", ...}} of an event's {@code data}.
     */
    public static String requestValue(String requestJson, String path, String caseLabel) {
        checkRequestPath(path, caseLabel);
        JsonNode node = Json.parse(requestJson);
        for (Object step : parsePath(path, caseLabel)) {
            if (step instanceof Integer n) {
                node = node.isArray() ? node.get(n) : null;
            } else if (node.isArray()) {
                JsonNode entry = null;
                for (JsonNode element : node) {
                    if (step.equals(element.path("key").asText(null))) {
                        entry = element.get("value");
                        break;
                    }
                }
                node = entry;
            } else {
                node = node.get((String) step);
            }
            if (node == null) return null;
        }
        if (node.isContainerNode() || node.isNull()) {
            throw new IllegalStateException("[ResponseMasker] the request of " + caseLabel + " has " + Json.compact(node)
                    + " at " + path + "; a request path names text, a number or a boolean");
        }
        return node.asText();
    }

    /**
     * {@code expectedJson} with every {@code ${var:<name>}} value replaced by the scenario variable (every
     * {@code ${request:<path>}} by the run's request value, a number equal by value): by the actual
     * value at the same place when it is the variable's value (a number stays a number), otherwise by the variable's
     * value as text, which the comparison then reports. A file without a {@code ${var:...}} value is returned as it is.
     */
    public static String fillVariables(String expectedJson, String actualJson, Function<String, String> variables,
                                       String caseLabel) {
        if (expectedJson == null || !expectedJson.contains(VAR_START) && !expectedJson.contains(REQUEST_START)) {
            return expectedJson;
        }
        JsonNode actual;
        try {
            actual = Json.parse(actualJson);
        } catch (IllegalStateException e) {
            actual = null;
        }
        return Json.pretty(fill(Json.parse(expectedJson), actual, variables, caseLabel));
    }

    private static JsonNode fill(JsonNode expected, JsonNode actual, Function<String, String> variables,
                                 String caseLabel) {
        if (expected.isTextual()) {
            Matcher placeholder = VAR.matcher(expected.textValue());
            if (!placeholder.matches()) return expected;
            boolean request = placeholder.group(2) != null;
            String value = variables.apply(request ? REQUEST_PREFIX + placeholder.group(2) : placeholder.group(1));
            if (value == null) {
                throw new IllegalStateException("[ResponseMasker] the expected file of " + caseLabel + " has "
                        + expected.textValue() + ", but " + (request ? "the request has no value at "
                        + placeholder.group(2) : "the scenario has no variable " + placeholder.group(1)));
            }
            boolean same = actual != null && actual.isValueNode() && !actual.isNull()
                    && (request ? sameValue(actual, value) : actual.asText().equals(value));
            return same ? actual : TextNode.valueOf(value);
        }
        if (expected.isObject()) {
            ObjectNode copy = Json.MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> field : expected.properties()) {
                JsonNode actualField = actual != null && actual.isObject() ? actual.get(field.getKey()) : null;
                copy.set(field.getKey(), fill(field.getValue(), actualField, variables, caseLabel));
            }
            return copy;
        }
        if (expected.isArray()) {
            ArrayNode copy = Json.MAPPER.createArrayNode();
            for (int i = 0; i < expected.size(); i++) {
                JsonNode actualElement = actual != null && actual.isArray() ? actual.get(i) : null;
                copy.add(fill(expected.get(i), actualElement, variables, caseLabel));
            }
            return copy;
        }
        return expected;
    }

    private static JsonNode leaf(JsonNode value, String where) {
        if (value.isNull()) return value;
        if (value.isTextual()) return TextNode.valueOf(ANY_STRING);
        if (value.isNumber()) return TextNode.valueOf(ANY_NUMBER);
        if (value.isBoolean()) return TextNode.valueOf(ANY_BOOLEAN);
        throw new IllegalStateException("[ResponseMasker] " + where + ": unsupported value " + value.getNodeType());
    }

    private static JsonNode tree(JsonNode value, String where) {
        if (value.isObject()) {
            ObjectNode copy = Json.MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> field : value.properties()) {
                copy.set(field.getKey(), tree(field.getValue(), where));
            }
            return copy;
        }
        if (value.isArray()) {
            ArrayNode copy = Json.MAPPER.createArrayNode();
            value.forEach(element -> copy.add(tree(element, where)));
            return copy;
        }
        return leaf(value, where);
    }

    // ── Paths ────────────────────────────────────────────────────────────────

    /** {@code true} for the {@code [*]} step of a parsed path (every array element). */
    public static boolean isAny(Object step) {
        return step == ANY;
    }

    /** {@code true} for the {@code *} key of a parsed path (every value of an object). */
    public static boolean isAnyKey(Object step) {
        return step == ANY_KEY;
    }

    /**
     * {@code data.trade.udf[*].value} -> {@code ["data", "trade", "udf", ANY, "value"]}: keys as String, indexes as
     * Integer, {@code [*]} as a value {@link #isAny} accepts, the key {@code *} as one {@link #isAnyKey} accepts.
     */
    public static List<Object> parsePath(String path, String caseLabel) {
        if (path == null || path.isEmpty()) {
            throw new IllegalStateException("[ResponseMasker] empty rule path in the rules of " + caseLabel);
        }
        List<Object> steps = new ArrayList<>();
        int i = 0;
        boolean needKey = !path.startsWith("[");
        while (i < path.length()) {
            if (needKey) {
                int end = i;
                while (end < path.length() && path.charAt(end) != '.' && path.charAt(end) != '[') end++;
                String key = path.substring(i, end);
                if (key.equals("*")) {
                    steps.add(ANY_KEY);
                } else if (PLAIN_KEY.matcher(key).matches()) {
                    steps.add(key);
                } else {
                    throw badPath(path, caseLabel);
                }
                i = end;
                needKey = false;
            } else if (path.charAt(i) == '.') {
                if (i + 1 == path.length()) throw badPath(path, caseLabel);
                i++;
                needKey = path.charAt(i) != '[';
                if (!needKey) throw badPath(path, caseLabel);
            } else if (path.charAt(i) == '[') {
                int end = path.indexOf(']', i);
                String index = end < 0 ? "" : path.substring(i + 1, end);
                if (index.equals("*")) {
                    steps.add(ANY);
                } else if (index.matches("\\d+")) {
                    steps.add(Integer.valueOf(index));
                } else {
                    throw badPath(path, caseLabel);
                }
                i = end + 1;
            } else {
                throw badPath(path, caseLabel);
            }
        }
        return steps;
    }

    private static IllegalStateException badPath(String path, String caseLabel) {
        return new IllegalStateException("[ResponseMasker] rule path '" + path + "' of " + caseLabel
                + " is not keys joined by '.' (* for every value of an object), with [n] or [*] for array elements");
    }

    /**
     * A path as a rule path: {@code data.trade.udf[0].value}. A key a path cannot name ({@code .}, {@code [} or
     * {@code ]} in it) becomes {@code *}, which matches it among the object's other keys.
     */
    public static String render(List<Object> path) {
        StringBuilder out = new StringBuilder();
        for (Object step : path) {
            if (step instanceof String key) {
                out.append(out.isEmpty() ? "" : ".").append(PLAIN_KEY.matcher(key).matches() ? key : "*");
            } else if (isAnyKey(step)) {
                out.append(out.isEmpty() ? "*" : ".*");
            } else {
                out.append('[').append(isAny(step) ? "*" : step).append(']');
            }
        }
        return out.toString();
    }

    /** Every concrete path (keys and indexes) in {@code node} that {@code steps} matches. */
    private static void collect(JsonNode node, List<Object> steps, int i, List<Object> at, List<List<Object>> out) {
        if (i == steps.size()) {
            out.add(new ArrayList<>(at));
            return;
        }
        Object step = steps.get(i);
        if (step instanceof String key) {
            if (node.isObject() && node.has(key)) {
                at.add(key);
                collect(node.get(key), steps, i + 1, at, out);
                at.removeLast();
            }
        } else if (step == ANY_KEY) {
            if (node.isObject()) {
                for (String key : List.copyOf(node.properties()).stream().map(Map.Entry::getKey).toList()) {
                    at.add(key);
                    collect(node.get(key), steps, i + 1, at, out);
                    at.removeLast();
                }
            }
        } else if (node.isArray()) {
            int from = step == ANY ? 0 : (Integer) step;
            int to = step == ANY ? node.size() : Math.min(from + 1, node.size());
            for (int n = from; n < to; n++) {
                at.add(n);
                collect(node.get(n), steps, i + 1, at, out);
                at.removeLast();
            }
        }
    }

    private static JsonNode at(JsonNode root, List<Object> path) {
        JsonNode node = root;
        for (Object step : path) {
            node = step instanceof String key ? node.get(key) : node.get((Integer) step);
        }
        return node;
    }

    /**
     * Puts {@code value} at {@code path} in {@code root}. A path whose parent an earlier rule already replaced
     * (e.g. {@code ignore} on an object) is left as that rule wrote it.
     */
    private static JsonNode set(JsonNode root, List<Object> path, JsonNode value) {
        if (path.isEmpty()) return value;
        JsonNode parent = root;
        for (Object step : path.subList(0, path.size() - 1)) {
            parent = step instanceof String key ? parent.get(key) : parent.get((Integer) step);
            if (parent == null) return root;
        }
        Object last = path.getLast();
        if (last instanceof String key && parent instanceof ObjectNode object && object.has(key)) {
            object.set(key, value);
        } else if (last instanceof Integer n && parent instanceof ArrayNode array && n < array.size()) {
            array.set(n, value);
        }
        return root;
    }
}
