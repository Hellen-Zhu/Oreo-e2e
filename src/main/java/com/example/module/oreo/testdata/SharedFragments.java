package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parts of recorded responses that several cases and events share: {@code data/api/expected/_shared/<name>.json},
 * e.g. {@code instrument/FX_TRF}, the instrument every trade API returns for a trade of that product. An expected file
 * refers to one at the place of the part, an object {@code {"_shared": "<name>"}}; where the case's part differs, the
 * reference holds only the differences: those at most {@link #INLINE_DEPTH} keys deep as JSON beside {@code _shared}
 * ({@code {"_shared": "basic/FX_TRF", "status": "Live"}}): an object merges into the part's object at that place, any
 * other value (an array among them) replaces the part's value, {@code _remove} lists the keys the case does not have;
 * deeper ones as JSON Patch ops in {@code _patch}, applied after, each a path and its value
 * ({@code {"op": "replace", "path": "/_value/notional/amount1", "value": 200000000}}). {@link #expand} turns the
 * references back into the full part when the file is read. The event data declares which parts are shared
 * ({@code sharedFragments}); {@code TestDataTool compact} writes the shared files and the references, never a person.
 */
public final class SharedFragments {

    /** The folder under the expected root; not an event. */
    public static final String FOLDER = "_shared";
    /** The key of a reference. */
    public static final String KEY = "_shared";
    /**
     * In a reference, at any depth: the keys of the part's object at that place that the case does not have
     * ({@code "_remove": ["ccy"]}).
     */
    public static final String REMOVE = "_remove";
    /** In a reference: JSON Patch ops on the part, for the differences deeper than {@link #INLINE_DEPTH} keys. */
    public static final String PATCH = "_patch";
    /**
     * How many keys deep a difference is still written as JSON beside {@code _shared} ({@code status},
     * {@code basic.status}); a deeper one is a {@code _patch} op, a path and its value.
     */
    public static final int INLINE_DEPTH = 2;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*");

    private final Path folder;

    public SharedFragments(Path expectedRoot) {
        this.folder = expectedRoot.resolve(FOLDER);
    }

    /** A fragment name: words of letters, digits, {@code _} or {@code -}, joined by {@code /}. */
    public static boolean isName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public Path fileFor(String name) {
        if (!isName(name)) {
            throw new IllegalArgumentException("[SharedFragments] '" + name + "' is not a fragment name"
                    + " (words of letters, digits, _ or -, joined by /)");
        }
        return folder.resolve(name + ".json");
    }

    /** The shared part; empty when it has no file. */
    public Optional<ObjectNode> read(String name) {
        Path file = fileFor(name);
        if (!Files.isRegularFile(file)) return Optional.empty();
        JsonNode json;
        try {
            json = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("[SharedFragments] cannot read " + file, e);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("[SharedFragments] " + file + ": " + e.getMessage(), e);
        }
        if (!json.isObject()) {
            throw new IllegalStateException("[SharedFragments] " + file + " must hold a JSON object");
        }
        if (json.toString().contains("\"" + KEY + "\"")) {
            throw new IllegalStateException("[SharedFragments] " + file + " refers to another shared part;"
                    + " a shared part is written in full");
        }
        return Optional.of((ObjectNode) json);
    }

    /** Writes a shared part (UTF-8, {@code \n} line ends), creating its folders. */
    public void write(String name, String json) {
        Path file = fileFor(name);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.replace("\r\n", "\n"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("[SharedFragments] cannot write " + file, e);
        }
    }

    public void delete(String name) {
        try {
            Files.deleteIfExists(fileFor(name));
        } catch (IOException e) {
            throw new UncheckedIOException("[SharedFragments] cannot delete " + fileFor(name), e);
        }
    }

    /** The names of every shared file, sorted. */
    public List<String> names() {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
                    .map(p -> folder.relativize(p).toString().replace('\\', '/').replaceFirst("\\.json$", ""))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("[SharedFragments] cannot list " + folder, e);
        }
    }

    /** An object with a text {@code _shared} key. */
    public static boolean isReference(JsonNode node) {
        return node != null && node.isObject() && node.path(KEY).isTextual();
    }

    /** The names {@code json} refers to, in document order. */
    public static List<String> references(JsonNode json) {
        List<String> out = new ArrayList<>();
        collect(json, out);
        return out;
    }

    private static void collect(JsonNode node, List<String> out) {
        if (isReference(node)) out.add(node.get(KEY).asText());
        node.forEach(child -> collect(child, out));
    }

    /**
     * {@code json} with every reference replaced by its shared part with the reference's differences merged in (an
     * object into the object at its place, any other value replacing it, {@code _remove} removing keys), then its
     * {@code _patch} ops applied. {@code file} is for messages.
     */
    public JsonNode expand(JsonNode json, Path file) {
        return expandNode(json.deepCopy(), file);
    }

    private JsonNode expandNode(JsonNode node, Path file) {
        if (isReference(node)) {
            String name = node.get(KEY).asText();
            ObjectNode part = read(name).orElseThrow(() -> new IllegalStateException("[SharedFragments] " + file
                    + " refers to the shared part '" + name + "', which has no file " + fileFor(name)
                    + ". To fix: TestDataTool compact all (it writes the shared parts again)"));
            ObjectNode full = part.deepCopy();
            merge(full, (ObjectNode) node, file + " (reference to " + name + ")");
            JsonNode ops = node.get(PATCH);
            return ops == null ? full : JsonPatch.apply(full, ops, file + " (reference to " + name + ")");
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            for (Map.Entry<String, JsonNode> entry : List.copyOf(object.properties())) {
                object.set(entry.getKey(), expandNode(entry.getValue(), file));
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                ((ArrayNode) node).set(i, expandNode(node.get(i), file));
            }
        }
        return node;
    }

    /** Merges the differences {@code diff} into {@code target}, as {@link #expand} does; skips the reference's keys. */
    private static void merge(ObjectNode target, ObjectNode diff, String label) {
        JsonNode removed = diff.get(REMOVE);
        if (removed != null) {
            if (!removed.isArray()) throw new IllegalStateException("[SharedFragments] " + label + ": " + REMOVE
                    + " must be an array of keys");
            removed.forEach(key -> target.remove(key.asText()));
        }
        for (Map.Entry<String, JsonNode> entry : diff.properties()) {
            String key = entry.getKey();
            if (key.equals(KEY) || key.equals(PATCH) || key.equals(REMOVE)) continue;
            JsonNode value = entry.getValue();
            if (value.isObject() && target.get(key) instanceof ObjectNode inner) {
                merge(inner, (ObjectNode) value, label);
            } else {
                target.set(key, value.deepCopy());
            }
        }
    }

    /**
     * The reference that stands for {@code actual} given the shared part {@code shared}: {@code _shared} plus only what
     * differs. A difference at most {@link #INLINE_DEPTH} keys deep is written as JSON beside {@code _shared}
     * ({@code "status": "Live"}, {@code "basic": {"status": "Live"}}; {@code _remove} for keys the case lacks); a
     * deeper one as a JSON Patch op in {@code _patch}, its path and value
     * ({@code {"op": "replace", "path": "/_value/notional/amount1", "value": 200000000}}). Values compare exactly
     * ({@code 2.0E+8} is not {@code 200000000}); key order does not count. {@code null} when {@code actual} holds a key
     * the reference uses itself ({@code _shared}, or {@code _remove} / {@code _patch} where it differs).
     */
    public static ObjectNode reference(String name, ObjectNode shared, ObjectNode actual) {
        if (actual.has(KEY)) return null;
        ObjectNode ref = Json.MAPPER.createObjectNode();
        ref.put(KEY, name);
        ArrayNode ops = Json.MAPPER.createArrayNode();
        if (!differences(shared, actual, ref, 1, "", ops)) return null;
        if (!ops.isEmpty()) ref.set(PATCH, ops);
        return ref;
    }

    /**
     * Writes into {@code out} what differs from {@code part} in {@code actual}, {@code level} keys deep; deeper
     * differences go to {@code ops}, under {@code pointer}. {@code false} when it cannot.
     */
    private static boolean differences(ObjectNode part, ObjectNode actual, ObjectNode out, int level, String pointer,
                                       ArrayNode ops) {
        if (actual.has(REMOVE) || actual.has(PATCH)) return false;
        ArrayNode removed = Json.MAPPER.createArrayNode();
        for (String key : (Iterable<String>) part::fieldNames) {
            if (!actual.has(key)) removed.add(key);
        }
        if (!removed.isEmpty()) out.set(REMOVE, removed);
        for (Map.Entry<String, JsonNode> entry : actual.properties()) {
            String key = entry.getKey();
            JsonNode mine = entry.getValue();
            JsonNode theirs = part.get(key);
            if (mine.equals(theirs)) continue;
            if (mine instanceof ObjectNode object && theirs instanceof ObjectNode partObject) {
                String inner = pointer + "/" + key.replace("~", "~0").replace("/", "~1");
                if (level < INLINE_DEPTH) {
                    ObjectNode nested = Json.MAPPER.createObjectNode();
                    if (!differences(partObject, object, nested, level + 1, inner, ops)) return false;
                    if (!nested.isEmpty()) out.set(key, nested); // empty: its differences are all deeper, in ops
                } else {
                    for (JsonNode op : JsonPatch.diff(partObject, object)) {
                        ObjectNode moved = ((ObjectNode) op).deepCopy();
                        moved.put("path", inner + op.path("path").asText());
                        ops.add(moved);
                    }
                }
            } else {
                out.set(key, mine.deepCopy());
            }
        }
        return true;
    }

    /** The places where a reference differs from its part, as paths ({@code _value.notional.amount1}). */
    public static List<String> differencePaths(JsonNode ref) {
        List<String> out = new ArrayList<>();
        differencePaths(ref, "", out);
        return out;
    }

    private static void differencePaths(JsonNode node, String at, List<String> out) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String key = entry.getKey();
            if (key.equals(KEY)) continue;
            if (key.equals(REMOVE)) {
                entry.getValue().forEach(k -> out.add(at + k.asText() + " (removed)"));
            } else if (key.equals(PATCH)) {
                entry.getValue().forEach(op -> out.add(op.path("path").asText()));
            } else if (entry.getValue().isObject() && !entry.getValue().isEmpty()) {
                differencePaths(entry.getValue(), at + key + ".", out);
            } else {
                out.add(at + key);
            }
        }
    }
}
