package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The merged data of one case: its request (body or form values), its dynamic-field rules and where they came from. */
public final class CaseData {

    private final CaseId id;
    private final ObjectNode request;
    private final Map<String, String> dynamicFields;
    private final Map<String, String> sharedFragments;
    private final String expectedBase;
    private final String datFile;
    private final List<String> layers;

    CaseData(CaseId id, ObjectNode request, Map<String, String> dynamicFields, Map<String, String> sharedFragments,
             String expectedBase, String datFile, List<String> layers) {
        this.id = id;
        this.request = request.deepCopy();
        this.dynamicFields = Collections.unmodifiableMap(new LinkedHashMap<>(dynamicFields));
        this.sharedFragments = Collections.unmodifiableMap(new LinkedHashMap<>(sharedFragments));
        this.expectedBase = expectedBase;
        this.datFile = datFile;
        this.layers = List.copyOf(layers);
    }

    public CaseId id() { return id; }

    /** Compact JSON of the merged request, {@code ${date:...}} filled in; {@code {}} when no layer has one. */
    public String request() { return Json.compact(DataPlaceholders.resolve(request)); }

    /** {@code true} when the merged request has at least one field. */
    public boolean hasRequest() { return !request.isEmpty(); }

    /** Path -> rule ({@code type}, {@code type-tree}, {@code regex:...}, {@code ignore}), API cases only. */
    public Map<String, String> dynamicFields() { return dynamicFields; }

    /**
     * Response path -> name of the shared part it is stored as ({@code data.trade.instrument} ->
     * {@code instrument/FX_TRF}, {@code <product_type>} already replaced); see {@link SharedFragments}. API cases only.
     */
    public Map<String, String> sharedFragments() { return sharedFragments; }

    /**
     * The case whose expected file this case's file is a patch on ({@code <product_type>} already replaced);
     * {@code null} when it has none (API cases only).
     */
    public String expectedBase() { return expectedBase; }

    /**
     * The dat file the case uploads or sends, a file name of its product's folder ({@code data/dat/<P>/}); {@code null}
     * for the product's default {@code <P>.dat}. See {@link ProductDatFiles#path(String)}.
     */
    public String datFile() { return datFile; }

    /** The layers applied, in order, e.g. {@code trade_creation.json defaults (use trade-basic)}. */
    public List<String> layers() { return layers; }

    /**
     * The request flattened into dot-path keys ({@code basic.counterpartyName}, {@code subTrades[0].portfolioId})
     * with text leaf values, as the hook stores them in the scenario context; {@code ${date:...}} filled in for today.
     */
    public Map<String, String> flattenRequest() {
        Map<String, String> result = new LinkedHashMap<>();
        flatten(DataPlaceholders.resolve(request), "", result);
        return Collections.unmodifiableMap(result);
    }

    private static void flatten(JsonNode node, String prefix, Map<String, String> result) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                flatten(field.getValue(), prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey(), result);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(node.get(i), prefix + "[" + i + "]", result);
            }
        } else if (!node.isNull() && !node.isMissingNode()) {
            result.put(prefix, node.asText());
        }
    }

    @Override
    public String toString() {
        return "CaseData{" + id.raw() + ", layers=" + layers + '}';
    }
}
