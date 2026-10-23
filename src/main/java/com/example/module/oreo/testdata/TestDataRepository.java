package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Loads case data ({@code <root><api|ui>/common.json}, {@code <root><api|ui>/<event>.json}; the {@code use} of a
 * layer names a block of either file's {@code shared}) and setup data
 * ({@code <root>setup/<name>.json}) from the classpath. Every file is read, stripped of {@code _comment} and
 * validated once, on first use; results are cached. All errors are {@link IllegalStateException}s naming the
 * file and the key.
 */
public final class TestDataRepository {

    private static final Set<String> COMMON_KEYS = Set.of("shared");
    private static final Set<String> BLOCK_KEYS = Set.of("request", "dynamicFields");
    private static final Set<String> EVENT_KEYS = Set.of("shared", "defaults", "byProduct", "cases");
    private static final Set<String> LAYER_KEYS = Set.of("use", "request", "dynamicFields", "expectedBase",
            "datFile");
    private static final Set<String> ROW_KEYS = Set.of("id", "use", "request", "dynamicFields", "expectedBase",
            "datFile", "byProduct");
    private static final Set<String> SETUP_KEYS = Set.of("extends", "request", "response");
    private static final Pattern SETUP_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final String root;
    private final ConcurrentHashMap<String, Optional<ObjectNode>> files = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CaseData> cases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SetupData> setups = new ConcurrentHashMap<>();

    /** @param classpathRoot the folder holding {@code api/}, {@code ui/} and {@code setup/}, e.g. {@code "data/"} */
    public TestDataRepository(String classpathRoot) {
        this.root = classpathRoot.endsWith("/") ? classpathRoot : classpathRoot + "/";
    }

    // ── Case data ────────────────────────────────────────────────────────────

    /** The merged data of a case; a case without a row runs on the defaults, a case without an event file has none. */
    public CaseData forCase(String caseId) {
        CaseData cached = cases.get(caseId);
        if (cached != null) return cached;
        CaseData built = build(parseCaseId(caseId));
        CaseData previous = cases.putIfAbsent(caseId, built);
        return previous != null ? previous : built;
    }

    private static CaseId parseCaseId(String caseId) {
        try {
            return CaseId.parse(caseId);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("[TestData] " + e.getMessage(), e);
        }
    }

    private CaseData build(CaseId id) {
        String eventPath = root + id.channelDir() + "/" + id.eventFile() + ".json";
        Optional<ObjectNode> eventFile = eventFile(id, eventPath);
        Merged merged = new Merged();
        if (eventFile.isEmpty()) {
            return merged.toCaseData(id);
        }
        ObjectNode event = eventFile.get();
        Map<String, JsonNode> shared = sharedBlocks(id.channelDir());
        event.path("shared").properties().forEach(e -> shared.put(e.getKey(), e.getValue()));
        String file = id.eventFile() + ".json";

        apply(merged, objectOrNull(event.get("defaults")), file + " defaults", shared, eventPath);
        if (id.product() != null) {
            apply(merged, objectOrNull(event.path("byProduct").get(id.product())),
                    file + " byProduct " + id.product(), shared, eventPath);
        }
        ObjectNode row = rowFor(id, event, eventPath);
        if (row != null) {
            String rowId = row.get("id").asText();
            apply(merged, row, "row " + rowId, shared, eventPath);
            if (id.product() != null) {
                apply(merged, objectOrNull(row.path("byProduct").get(id.product())),
                        "row " + rowId + " byProduct " + id.product(), shared, eventPath);
            }
        }
        checkDatFileExists(merged, id, eventPath);
        return merged.toCaseData(id);
    }

    /** The {@code datFile} the layers chose is a file of the case's product (a case without a product: by its step). */
    private void checkDatFileExists(Merged merged, CaseId id, String eventPath) {
        if (merged.datFile == null || id.product() == null) return;
        String datPath = root + "dat/" + id.product() + "/" + merged.datFile;
        if (classLoader().getResource(datPath) == null) {
            throw error(eventPath, merged.datFileFrom + " datFile", "no dat file " + datPath
                    + " on the classpath: a variant is a file of the product's folder");
        }
    }

    private ObjectNode rowFor(CaseId id, ObjectNode event, String eventPath) {
        ObjectNode found = null;
        for (JsonNode row : event.path("cases")) {
            if (id.matchesRow(row.get("id").asText())) {
                if (found != null) {
                    throw error(eventPath, "cases", "case " + id.raw() + " is matched by two rows, '"
                            + found.get("id").asText() + "' and '" + row.get("id").asText()
                            + "': one scenario, one row (put product differences into the row's byProduct)");
                }
                found = (ObjectNode) row;
            }
        }
        return found;
    }

    /**
     * Applies one layer: its {@code use} blocks in order, then its own {@code request}, {@code dynamicFields} and
     * {@code expectedBase}.
     */
    private static void apply(Merged merged, ObjectNode layer, String label, Map<String, JsonNode> shared,
                              String path) {
        if (layer == null) return;
        List<String> uses = new ArrayList<>();
        for (JsonNode use : layer.path("use")) {
            String name = use.asText();
            JsonNode block = shared.get(name);
            if (block == null) {
                throw error(path, label + " use", "no shared block '" + name + "' in common.json or this file "
                        + shared.keySet());
            }
            add(merged, block, label + " use " + name, path);
            uses.add(name);
        }
        add(merged, layer, label, path);
        if (layer.has("datFile")) {
            merged.datFile = layer.get("datFile").isNull() ? null : layer.get("datFile").asText();
            merged.datFileFrom = label;
        }
        merged.layers.add(uses.isEmpty() ? label : label + " (use " + String.join(", ", uses) + ")");
    }

    /** {@link Merged#add}, with an {@code _append} that has nothing to add to reported against the layer. */
    private static void add(Merged merged, JsonNode part, String label, String path) {
        try {
            merged.add(part);
        } catch (IllegalStateException e) {
            throw error(path, label + " request", e.getMessage());
        }
    }

    /** The request, dynamic fields, expected base and dat file merged so far. */
    private static final class Merged {
        ObjectNode request = Json.MAPPER.createObjectNode();
        final Map<String, String> dynamicFields = new LinkedHashMap<>();
        String expectedBase;
        String datFile;
        String datFileFrom;
        final List<String> layers = new ArrayList<>();

        void add(JsonNode part) {
            JsonNode base = part.get("expectedBase");
            if (base != null) {
                expectedBase = base.isNull() ? null : base.asText();
            }
            JsonNode req = part.get("request");
            if (req != null) {
                request = JsonLayers.merge(request, (ObjectNode) req);
            }
            JsonNode dyn = part.get("dynamicFields");
            if (dyn == null) return;
            if (dyn.isNull()) {
                dynamicFields.clear();
                return;
            }
            for (Map.Entry<String, JsonNode> rule : dyn.properties()) {
                if (rule.getValue().isNull()) {
                    dynamicFields.remove(rule.getKey());
                } else {
                    dynamicFields.put(rule.getKey(), rule.getValue().asText());
                }
            }
        }

        CaseData toCaseData(CaseId id) {
            return new CaseData(id, request, dynamicFields, resolveBase(id), datFile, layers);
        }

        /**
         * The base case ID: {@code <...>} replaced by the case's product; none on a case without a product when
         * the value names one, and none when it is the case itself.
         */
        private String resolveBase(CaseId id) {
            if (expectedBase == null) return null;
            String resolved = expectedBase;
            if (resolved.contains("<")) {
                if (id.product() == null) return null;
                resolved = resolved.replaceAll("<[^>]+>", id.product());
            }
            return resolved.equals(id.raw()) ? null : resolved;
        }
    }

    // ── Case data files and their validation ─────────────────────────────────

    private Optional<ObjectNode> eventFile(CaseId id, String path) {
        return load(path, json -> validateEvent(json, path, id));
    }

    private Map<String, JsonNode> sharedBlocks(String channelDir) {
        String path = root + channelDir + "/common.json";
        Optional<ObjectNode> common = load(path, json -> validateCommon(json, path, channelDir));
        Map<String, JsonNode> blocks = new LinkedHashMap<>();
        common.ifPresent(c -> c.path("shared").properties().forEach(e -> blocks.put(e.getKey(), e.getValue())));
        return blocks;
    }

    private void validateCommon(ObjectNode common, String path, String channelDir) {
        checkKeys(common, COMMON_KEYS, path, "");
        checkBlocks(common.get("shared"), path, channelDir);
    }

    /** The {@code shared} blocks of {@code common.json} or of an event file. */
    private static void checkBlocks(JsonNode shared, String path, String channelDir) {
        if (shared == null) return;
        requireObject(shared, path, "shared");
        for (Map.Entry<String, JsonNode> block : shared.properties()) {
            String where = "shared." + block.getKey();
            requireObject(block.getValue(), path, where);
            checkKeys((ObjectNode) block.getValue(), BLOCK_KEYS, path, where);
            checkRequest(block.getValue(), path, where);
            checkDynamicFields(block.getValue(), path, where, channelDir);
        }
    }

    private void validateEvent(ObjectNode event, String path, CaseId id) {
        String channelDir = id.channelDir();
        Set<String> shared = new TreeSet<>(sharedBlocks(channelDir).keySet());
        checkKeys(event, EVENT_KEYS, path, "");
        checkBlocks(event.get("shared"), path, channelDir);
        for (Map.Entry<String, JsonNode> block : event.path("shared").properties()) {
            if (!shared.add(block.getKey())) {
                throw error(path, "shared." + block.getKey(), "a block of that name is in " + channelDir
                        + "/common.json too: give this one another name");
            }
        }
        JsonNode defaults = event.get("defaults");
        if (defaults != null) {
            checkLayer(defaults, LAYER_KEYS, path, "defaults", shared, id);
        }
        checkByProduct(event.get("byProduct"), path, "byProduct", shared, id);
        JsonNode rows = event.get("cases");
        if (rows == null) return;
        if (!rows.isArray()) {
            throw error(path, "cases", "must be an array of rows");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            String where = "cases[" + i + "]";
            JsonNode rowId = row.get("id");
            if (rowId == null || !rowId.isTextual()) {
                throw error(path, where + ".id", "every row needs an id (the case ID as written in the feature)");
            }
            where = "row " + rowId.asText();
            checkCaseIdPattern(rowId.asText(), id, path, where, "a row");
            if (!seen.add(rowId.asText())) {
                throw error(path, where, "the row ID appears twice");
            }
            checkLayer(row, ROW_KEYS, path, where, shared, id);
            checkByProduct(row.get("byProduct"), path, where + " byProduct", shared, id);
        }
    }

    /** A row ID or an {@code expectedBase}: written like a row ID, a case of this file's event and channel. */
    private static void checkCaseIdPattern(String value, CaseId id, String path, String where, String what) {
        CaseId example;
        try {
            CaseId.checkRowId(value);
            example = CaseId.parse(value.replaceAll("<[^>]+>", ProductDatFiles.values()[0].name()));
        } catch (IllegalArgumentException e) {
            throw error(path, where, e.getMessage());
        }
        if (!example.event().equals(id.event()) || !example.channel().equals(id.channel())) {
            throw error(path, where, what + " of " + id.eventFile() + ".json must be a TC-" + id.event() + "-..."
                    + id.channel() + "-NNN case");
        }
    }

    private void checkByProduct(JsonNode byProduct, String path, String where, Set<String> shared, CaseId id) {
        if (byProduct == null) return;
        requireObject(byProduct, path, where);
        Set<String> products = new LinkedHashSet<>();
        for (ProductDatFiles p : ProductDatFiles.values()) products.add(p.name());
        for (Map.Entry<String, JsonNode> entry : byProduct.properties()) {
            if (!products.contains(entry.getKey())) {
                throw error(path, where + "." + entry.getKey(), "not a product of ProductDatFiles " + products);
            }
            checkLayer(entry.getValue(), LAYER_KEYS, path, where + "." + entry.getKey(), shared, id);
        }
    }

    private void checkLayer(JsonNode layer, Set<String> allowed, String path, String where, Set<String> shared,
                            CaseId id) {
        String channelDir = id.channelDir();
        requireObject(layer, path, where);
        checkKeys((ObjectNode) layer, allowed, path, where);
        JsonNode use = layer.get("use");
        if (use != null) {
            if (!use.isArray()) {
                throw error(path, where + ".use", "must be an array of shared block names");
            }
            for (JsonNode name : use) {
                if (!name.isTextual() || !shared.contains(name.asText())) {
                    throw error(path, where + ".use", "no shared block " + name + " in "
                            + channelDir + "/common.json or this file's shared " + shared);
                }
            }
        }
        checkRequest(layer, path, where);
        checkDynamicFields(layer, path, where, channelDir);
        checkExpectedBase(layer, path, where, id);
        checkDatFile(layer, path, where);
    }

    private static void checkDatFile(JsonNode layer, String path, String where) {
        JsonNode datFile = layer.get("datFile");
        if (datFile == null || datFile.isNull()) return;
        try {
            ProductDatFiles.checkFileName(datFile.isTextual() ? datFile.asText() : null);
        } catch (IllegalArgumentException e) {
            throw error(path, where + ".datFile", e.getMessage() + "; or null for the product's default");
        }
    }

    private static void checkExpectedBase(JsonNode layer, String path, String where, CaseId id) {
        JsonNode base = layer.get("expectedBase");
        if (base == null) return;
        if (!id.channelDir().equals("api")) {
            throw error(path, where + ".expectedBase", "expectedBase exists in data/api/ only (UI has no recorded responses)");
        }
        if (base.isNull()) return;
        if (!base.isTextual()) {
            throw error(path, where + ".expectedBase", "must be a case ID (with <product_type> for the case's product) or null");
        }
        checkCaseIdPattern(base.asText(), id, path, where + ".expectedBase", "expectedBase");
    }

    private static void checkRequest(JsonNode part, String path, String where) {
        JsonNode request = part.get("request");
        if (request == null) return;
        if (!request.isObject()) {
            throw error(path, where + ".request", "must be a JSON object (the request body or the form values)");
        }
        checkValues(request, path, where + ".request");
    }

    /** The {@code _append} directives and the {@code ${date:...}} placeholders of a request or a response. */
    private static void checkValues(JsonNode part, String path, String where) {
        try {
            JsonLayers.checkAppends(part);
            DataPlaceholders.check(part);
        } catch (IllegalArgumentException e) {
            throw error(path, where, e.getMessage());
        }
    }

    private static void checkDynamicFields(JsonNode part, String path, String where, String channelDir) {
        JsonNode dyn = part.get("dynamicFields");
        if (dyn == null) return;
        if (!channelDir.equals("api")) {
            throw error(path, where + ".dynamicFields", "dynamicFields exist in data/api/ only (UI has no recorded responses)");
        }
        if (dyn.isNull()) return;
        if (!dyn.isObject()) {
            throw error(path, where + ".dynamicFields", "must be an object of path -> rule, or null");
        }
        for (Map.Entry<String, JsonNode> rule : dyn.properties()) {
            if (!rule.getValue().isTextual() && !rule.getValue().isNull()) {
                throw error(path, where + ".dynamicFields." + rule.getKey(),
                        "a rule is a string (type, type-tree, regex:<pattern>, var:<name>, ignore) or null");
            }
        }
    }

    /**
     * Loads and validates {@code <root><channelDir>/common.json} and, unless {@code eventFile} is {@code null},
     * {@code <root><channelDir>/<eventFile>.json}, as {@link #forCase} does (for the offline checks, which list
     * the files themselves).
     */
    public void validate(String channelDir, String eventFile) {
        sharedBlocks(channelDir);
        if (eventFile == null) return;
        String channel = channelDir.toUpperCase(Locale.ROOT);
        String event = eventFile.toUpperCase(Locale.ROOT).replace('_', '-');
        CaseId any = new CaseId("TC-" + event + "-" + channel + "-000", event, null, channel, "000");
        String path = root + channelDir + "/" + eventFile + ".json";
        if (eventFile(any, path).isEmpty()) {
            throw new IllegalStateException("[TestData] " + path + " not found on the classpath");
        }
    }

    // ── Setup data ───────────────────────────────────────────────────────────

    /** The setup payload {@code <root>setup/<name>.json}, with its {@code extends} chain merged under it. */
    public SetupData setup(String name) {
        SetupData cached = setups.get(name);
        if (cached != null) return cached;
        SetupData built = resolveSetup(name, new ArrayList<>());
        SetupData previous = setups.putIfAbsent(name, built);
        return previous != null ? previous : built;
    }

    private SetupData resolveSetup(String name, List<String> chain) {
        if (name == null || !SETUP_NAME.matcher(name).matches()) {
            throw new IllegalStateException("[TestData] '" + name + "' is not a setup data name"
                    + " (the file name in " + root + "setup/ without .json: lower case, digits and -)"
                    + via(chain));
        }
        if (chain.contains(name)) {
            List<String> loop = new ArrayList<>(chain);
            loop.add(name);
            throw new IllegalStateException("[TestData] setup data extends itself: " + String.join(" -> ", loop));
        }
        String path = root + "setup/" + name + ".json";
        ObjectNode file = load(path, json -> validateSetup(json, path)).orElseThrow(() ->
                new IllegalStateException("[TestData] No setup data '" + name + "': " + path + " not found"
                        + via(chain)));
        ObjectNode request = objectOrNull(file.get("request"));
        ObjectNode response = objectOrNull(file.get("response"));
        JsonNode parentName = file.get("extends");
        SetupData parent = null;
        if (parentName != null) {
            chain.add(name);
            parent = resolveSetup(parentName.asText(), chain);
            chain.remove(chain.size() - 1);
        }
        request = under(parent == null ? null : parent.requestNode(), request, path, "request");
        response = under(parent == null ? null : parent.responseNode(), response, path, "response");
        return new SetupData(name, path, request, response);
    }

    /** {@code child} merged over {@code parent}; an {@code _append} in {@code child} needs the parent's part. */
    private static ObjectNode under(ObjectNode parent, ObjectNode child, String path, String part) {
        if (child == null) return parent;
        if (parent == null) {
            if (JsonLayers.hasAppend(child)) {
                throw error(path, part, "an _append adds to a list of the setup this one extends, and there is no "
                        + part + " to add to (no extends, or the setup it extends has no " + part + ")");
            }
            return child;
        }
        try {
            return JsonLayers.merge(parent, child);
        } catch (IllegalStateException e) {
            throw error(path, part, e.getMessage());
        }
    }

    private static String via(List<String> chain) {
        return chain.isEmpty() ? "" : " (extended by " + String.join(" <- ", chain.reversed()) + ")";
    }

    private static void validateSetup(ObjectNode setup, String path) {
        checkKeys(setup, SETUP_KEYS, path, "");
        JsonNode parent = setup.get("extends");
        if (parent != null && !parent.isTextual()) {
            throw error(path, "extends", "must be the name of another setup file, without .json");
        }
        for (String key : List.of("request", "response")) {
            JsonNode part = setup.get(key);
            if (part == null) continue;
            if (!part.isObject()) {
                throw error(path, key, "must be a JSON object");
            }
            checkValues(part, path, key);
        }
    }

    // ── Files ────────────────────────────────────────────────────────────────

    /** Reads, strips and validates a file once; an absent file is cached as empty. A broken file is never cached. */
    private Optional<ObjectNode> load(String path, Consumer<ObjectNode> validator) {
        Optional<ObjectNode> cached = files.get(path);
        if (cached != null) return cached;
        Optional<ObjectNode> loaded = read(path);
        loaded.ifPresent(validator);
        Optional<ObjectNode> previous = files.putIfAbsent(path, loaded);
        return previous != null ? previous : loaded;
    }

    private static Optional<ObjectNode> read(String path) {
        try (InputStream in = classLoader().getResourceAsStream(path)) {
            if (in == null) return Optional.empty();
            JsonNode json;
            try {
                json = Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IllegalStateException e) {
                throw new IllegalStateException("[TestData] " + path + ": " + e.getMessage(), e);
            }
            if (!json.isObject()) {
                throw error(path, "", "the file must hold a JSON object");
            }
            return Optional.of((ObjectNode) JsonLayers.withoutComments(json));
        } catch (IOException e) {
            throw new IllegalStateException("[TestData] cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ClassLoader classLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : TestDataRepository.class.getClassLoader();
    }

    private static void checkKeys(ObjectNode node, Set<String> allowed, String path, String where) {
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!allowed.contains(field.getKey())) {
                throw error(path, where.isEmpty() ? field.getKey() : where + "." + field.getKey(),
                        "unknown key; allowed here: " + new TreeSet<>(allowed) + " and _comment");
            }
        }
    }

    private static void requireObject(JsonNode node, String path, String where) {
        if (!node.isObject()) {
            throw error(path, where, "must be a JSON object");
        }
    }

    private static ObjectNode objectOrNull(JsonNode node) {
        return node instanceof ObjectNode object ? object : null;
    }

    private static IllegalStateException error(String path, String where, String message) {
        return new IllegalStateException("[TestData] " + path + (where.isEmpty() ? "" : " [" + where + "]")
                + ": " + message);
    }
}
