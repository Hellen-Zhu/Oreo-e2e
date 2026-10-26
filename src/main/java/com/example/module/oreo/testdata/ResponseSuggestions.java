package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggestions read from the recorded responses of one event; nothing is written ({@code TestDataTool suggest}).
 *
 * <ul>
 *   <li>{@link #dynamicValues}: the plain values of a recording that look generated (a date, a time, a UUID, an ID
 *       such as {@code CHK-12345}, an epoch time) or that changed since the committed recording of the same case;
 *       {@link #placeRules} turns them into {@code dynamicFields} rules and the layer each belongs in.</li>
 *   <li>{@link #bases}: per product, the case whose response, as the base of the others, leaves the fewest characters
 *       in the event's files (what the compaction would write), and whether one {@code <product_type>} base fits
 *       every product.</li>
 * </ul>
 * A suggestion is evidence for a person to judge, never a rule: a date the request fixes is not dynamic, a value that
 * differs per product is data. Mask first, then choose the base: the base is measured on masked recordings.
 */
public final class ResponseSuggestions {

    private ResponseSuggestions() {}

    // ── dynamicFields ────────────────────────────────────────────────────────

    /** A value pattern: its name and the regular expression of a whole value. */
    private record Kind(String name, Pattern whole) {}

    private static final List<Kind> KINDS = List.of(
            new Kind("date-time", Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:?\\d{2})?")),
            new Kind("date", Pattern.compile("\\d{4}-\\d{2}-\\d{2}|\\d{2}-[A-Z][a-z]{2}-\\d{4}|\\d{4}\\d{2}\\d{2}T\\d{6}")),
            new Kind("UUID", Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")),
            new Kind("generated ID", Pattern.compile("[A-Z]{2,8}[-_]?\\d{5,}(-\\d+)*")),
            new Kind("hex trace ID", Pattern.compile("[0-9a-f]{16,64}")),
            new Kind("epoch time", Pattern.compile("1\\d{9}(\\d{3})?")));

    /** The same patterns inside a longer text ({@code TaskId: CHK-12345}): a regex rule. */
    private static final Pattern EMBEDDED = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}([T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?)?"
                    + "|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    + "|(?<![A-Za-z])[A-Z]{2,8}[-_]?\\d{5,}(-\\d+)*");

    /** One plain value of a recording that looks dynamic: its concrete path, the rule, and why. */
    public record Finding(String path, String rule, String reason) {}

    /**
     * The values of {@code actual} that look dynamic. {@code previous}: the committed recording of the same case,
     * {@code null} when there is none; a plain leaf that differs from it is dynamic whatever it looks like.
     * {@code request}: the case's request text, to say when a value is one the request sends. Placeholders are
     * skipped: a rule already masks them.
     */
    public static List<Finding> dynamicValues(JsonNode actual, JsonNode previous, String request) {
        List<Finding> out = new ArrayList<>();
        walk(actual, previous, "", request == null ? "" : request, out);
        return out;
    }

    private static void walk(JsonNode node, JsonNode previous, String at, String request, List<Finding> out) {
        if (node.isObject()) {
            node.properties().forEach(e -> {
                String step = ResponseMasker.render(List.of(e.getKey()));
                walk(e.getValue(), previous == null ? null : previous.get(e.getKey()),
                        at.isEmpty() ? step : at + "." + step, request, out);
            });
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walk(node.get(i), previous == null || !previous.isArray() ? null : previous.get(i),
                        at + "[" + i + "]", request, out);
            }
            return;
        }
        if (node.isNull() || node.isBoolean() || isPlaceholder(node)) return;
        String text = node.asText();
        String echo = request.contains("\"" + text + "\"") || request.contains(":" + text + ",")
                ? "; the request sends this value: dynamic only when the request's value is (${date:...})" : "";
        for (Kind kind : KINDS) {
            if (kind.whole().matcher(text).matches()) {
                out.add(new Finding(at, "type", "looks like a " + kind.name() + " (" + text + ")" + echo));
                return;
            }
        }
        if (node.isTextual()) {
            Matcher m = EMBEDDED.matcher(text);
            StringBuilder regex = new StringBuilder();
            int from = 0;
            List<String> tokens = new ArrayList<>();
            while (m.find()) {
                regex.append(quote(text.substring(from, m.start()))).append(".*");
                tokens.add(m.group());
                from = m.end();
            }
            if (!tokens.isEmpty()) {
                regex.append(quote(text.substring(from)));
                out.add(new Finding(at, "regex:" + regex, "a text holding " + String.join(", ", tokens)));
                return;
            }
        }
        if (previous != null && !previous.isMissingNode() && !isPlaceholder(previous) && !previous.equals(node)) {
            out.add(new Finding(at, "type", "changed since the committed recording (" + Json.compact(previous)
                    + " -> " + Json.compact(node) + ")"));
        }
    }

    /** {@code text} as a regular expression that matches it literally, without \Q...\E (easier to read). */
    static String quote(String text) {
        return text.replaceAll("([\\\\.\\[\\]{}()*+?^$|])", "\\\\$1");
    }

    private static boolean isPlaceholder(JsonNode node) {
        return node.isTextual() && (node.textValue().startsWith("${json-unit.")
                || node.textValue().startsWith(ResponseMasker.VAR_START));
    }

    /** A suggested rule and where it goes: {@code defaults}, {@code byProduct.<P>}, or the row of a case. */
    public record Rule(String layer, String path, String rule, List<String> cases, String reason) {}

    /**
     * Places the findings of every case of an event. An array index found in several elements becomes {@code [*]}.
     * A rule goes in {@code defaults} when every case with the path has it and every product has such a case (a case
     * without the path then needs {@code "dynamicFields": null} in its row, as the negative cases have), in
     * {@code byProduct.<P>} when every case of product P has it, otherwise in the row of each case.
     *
     * @param findings case ID -> its findings
     * @param has      case ID, rule path -> the case's recording has the path
     */
    public static List<Rule> placeRules(Map<String, List<Finding>> findings,
                                        java.util.function.BiPredicate<String, String> has) {
        // path with [*] -> case -> rule
        Map<String, Map<String, Finding>> byPath = new TreeMap<>();
        for (Map.Entry<String, List<Finding>> c : findings.entrySet()) {
            Map<String, List<Finding>> general = new LinkedHashMap<>();
            for (Finding f : c.getValue()) {
                general.computeIfAbsent(f.path().replaceAll("\\[\\d+]", "[*]"), k -> new ArrayList<>()).add(f);
            }
            for (Map.Entry<String, List<Finding>> g : general.entrySet()) {
                boolean several = g.getValue().size() > 1;
                for (Finding f : g.getValue()) {
                    String path = several ? g.getKey() : f.path();
                    byPath.computeIfAbsent(path, k -> new TreeMap<>()).putIfAbsent(c.getKey(), f);
                }
            }
        }
        Set<String> allCases = new TreeSet<>(findings.keySet());
        List<Rule> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Finding>> p : byPath.entrySet()) {
            String path = p.getKey();
            Map<String, Finding> flagged = p.getValue();
            String rule = mostCommonRule(flagged);
            String reason = flagged.values().iterator().next().reason();
            Set<String> withPath = new TreeSet<>();
            for (String c : allCases) if (has.test(c, path)) withPath.add(c);
            Map<String, Set<String>> byProduct = new TreeMap<>();
            for (String c : allCases) {
                String product = CaseId.parse(c).product();
                if (product != null) byProduct.computeIfAbsent(product, k -> new TreeSet<>()).add(c);
            }
            if (withPath.size() > 1 && flagged.keySet().containsAll(withPath)
                    && byProduct.values().stream().allMatch(cases -> cases.stream().anyMatch(withPath::contains))) {
                out.add(new Rule("defaults", path, rule, List.copyOf(flagged.keySet()), reason));
                continue;
            }
            Set<String> placed = new TreeSet<>();
            for (Map.Entry<String, Set<String>> product : byProduct.entrySet()) {
                Set<String> cases = product.getValue();
                if (cases.size() > 1 && flagged.keySet().containsAll(cases)) {
                    out.add(new Rule("byProduct." + product.getKey(), path, rule, List.copyOf(cases), reason));
                    placed.addAll(cases);
                }
            }
            for (String c : flagged.keySet()) {
                if (!placed.contains(c)) {
                    out.add(new Rule("row " + c, path, flagged.get(c).rule(), List.of(c), flagged.get(c).reason()));
                }
            }
        }
        out.sort(Comparator.comparing((Rule r) -> layerOrder(r.layer())).thenComparing(Rule::layer)
                .thenComparing(Rule::path));
        return out;
    }

    private static int layerOrder(String layer) {
        return layer.equals("defaults") ? 0 : layer.startsWith("byProduct") ? 1 : 2;
    }

    private static String mostCommonRule(Map<String, Finding> flagged) {
        Map<String, Integer> counts = new TreeMap<>();
        flagged.values().forEach(f -> counts.merge(f.rule(), 1, Integer::sum));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    }

    /** {@code true} when {@code json} has the rule path ({@code [*]}: in at least one element). */
    public static boolean has(JsonNode json, String path) {
        return has(json, ResponseMasker.parsePath(path, "suggestion"), 0);
    }

    private static boolean has(JsonNode node, List<Object> steps, int i) {
        if (node == null || node.isMissingNode()) return false;
        if (i == steps.size()) return true;
        Object step = steps.get(i);
        if (step instanceof String key) return node.isObject() && has(node.get(key), steps, i + 1);
        if (ResponseMasker.isAnyKey(step)) {
            if (!node.isObject()) return false;
            for (JsonNode value : node) if (has(value, steps, i + 1)) return true;
            return false;
        }
        if (!node.isArray()) return false;
        if (ResponseMasker.isAny(step)) {
            for (JsonNode element : node) if (has(element, steps, i + 1)) return true;
            return false;
        }
        return has(node.get((Integer) step), steps, i + 1);
    }

    // ── expectedBase ─────────────────────────────────────────────────────────

    /**
     * The best base of one product: the case, the characters of the product's files with it and with none, and the
     * form each other case would take.
     */
    public record Base(String product, String caseId, int withBase, int allFull, Map<String, String> forms,
                       Map<String, Integer> totals) {
        public boolean worthIt() {
            return withBase < allFull;
        }

        /** Characters with {@code caseId} as the base; {@code null} when it is not a case of this product. */
        public Integer with(String caseId) {
            return totals.get(caseId);
        }

        /**
         * {@code true} when {@code declared} leaves at most 10% more characters than the best base: keep it (the
         * plain case is the easier base to read a patch against).
         */
        public boolean declaredIsGoodEnough(String declared) {
            Integer total = declared == null ? null : totals.get(declared);
            return total != null && total < allFull && total <= withBase * 1.1;
        }
    }

    /**
     * Per product with two cases or more: the case that, as the base of the others, leaves the fewest characters
     * (each other case written as its patch or in full, whichever is shorter, as the compaction does). A tie goes to
     * the lowest case number. Cases without a product have no base and are left out.
     *
     * @param responses case ID -> its full (resolved) response
     */
    public static List<Base> bases(Map<String, JsonNode> responses) {
        Map<String, List<String>> byProduct = new TreeMap<>();
        for (String c : new TreeSet<>(responses.keySet())) {
            String product = CaseId.parse(c).product();
            if (product != null) byProduct.computeIfAbsent(product, k -> new ArrayList<>()).add(c);
        }
        List<Base> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> product : byProduct.entrySet()) {
            List<String> cases = product.getValue();
            if (cases.size() < 2) continue;
            int allFull = 0;
            for (String c : cases) allFull += Json.pretty(responses.get(c)).length();
            Base best = null;
            Map<String, Integer> totals = new TreeMap<>();
            for (String candidate : cases) {
                JsonNode base = responses.get(candidate);
                int total = Json.pretty(base).length();
                Map<String, String> forms = new TreeMap<>();
                for (String c : cases) {
                    if (c.equals(candidate)) continue;
                    JsonNode own = responses.get(c);
                    ArrayNode ops = JsonPatch.diff(base, own);
                    ObjectNode patch = Json.MAPPER.createObjectNode();
                    patch.put("_base", candidate);
                    patch.set("_patch", ops);
                    int patchLength = Json.pretty(patch).length();
                    int fullLength = Json.pretty(own).length();
                    total += Math.min(patchLength, fullLength);
                    forms.put(c, patchLength < fullLength ? "patch, " + ops.size() + " ops" : "full");
                }
                totals.put(candidate, total);
                if (best == null || total < best.withBase()) {
                    best = new Base(product.getKey(), candidate, total, allFull, forms, totals);
                }
            }
            out.add(best);
        }
        return out;
    }

    /**
     * The {@code expectedBase} value for {@code defaults} when every product's best base has the same number
     * ({@code TC-TRADE-CREATION-<product_type>-API-001}); {@code null} when they differ or none is worth it.
     */
    public static String commonBase(List<Base> bases) {
        Set<String> patterns = new TreeSet<>();
        for (Base b : bases) {
            if (!b.worthIt()) continue;
            CaseId id = CaseId.parse(b.caseId());
            patterns.add(id.raw().replace("-" + id.product() + "-" + id.channel() + "-",
                    "-<product_type>-" + id.channel() + "-"));
        }
        return patterns.size() == 1 && bases.stream().allMatch(Base::worthIt) ? patterns.iterator().next() : null;
    }
}
