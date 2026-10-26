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
 * refers to one at the place of the part, an object {@code {"_shared": "<name>"}}; its other keys replace the shared
 * file's keys of the same name ({@code {"_shared": "basic/FX_TRF", "status": "Live"}}). {@link #expand} turns the
 * references back into the full part when the file is read. The event data declares which parts are shared
 * ({@code sharedFragments}); {@code TestDataTool compact} writes the shared files and the references, never a person.
 */
public final class SharedFragments {

    /** The folder under the expected root; not an event. */
    public static final String FOLDER = "_shared";
    /** The key of a reference. */
    public static final String KEY = "_shared";

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
     * {@code json} with every reference replaced by its shared part, the reference's other keys replacing the part's
     * keys of the same name. {@code file} is for messages.
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
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (!entry.getKey().equals(KEY)) full.set(entry.getKey(), entry.getValue());
            }
            return full;
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

    /**
     * The reference that stands for {@code actual} given the shared part {@code shared}: {@code _shared} plus every
     * key of {@code actual} that the part lacks or holds with another value. {@code null} when the part has a key
     * that {@code actual} lacks (a reference cannot remove a key), or when {@code actual} already contains a key
     * {@code _shared}.
     */
    public static ObjectNode reference(String name, ObjectNode shared, ObjectNode actual) {
        if (actual.has(KEY)) return null;
        for (String key : (Iterable<String>) shared::fieldNames) {
            if (!actual.has(key)) return null;
        }
        ObjectNode ref = Json.MAPPER.createObjectNode();
        ref.put(KEY, name);
        for (Map.Entry<String, JsonNode> entry : actual.properties()) {
            if (!entry.getValue().equals(shared.get(entry.getKey()))) {
                ref.set(entry.getKey(), entry.getValue());
            }
        }
        return ref;
    }
}
