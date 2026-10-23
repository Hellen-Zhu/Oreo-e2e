package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;

/**
 * Merging of data layers: objects merge deeply, arrays and scalars replace, {@code null} deletes the key, and
 * {@code {"_append": [...]}} adds its elements to the end of the list an earlier layer gave that key.
 */
public final class JsonLayers {

    public static final String COMMENT = "_comment";
    public static final String APPEND = "_append";

    private JsonLayers() {}

    /**
     * A new object: {@code patch} applied over {@code base}. Neither input is changed. An {@code _append} on a key
     * that has no list in {@code base}, or of an element whose {@code key} the list already has, is an
     * {@link IllegalStateException} naming the key's path.
     */
    public static ObjectNode merge(ObjectNode base, ObjectNode patch) {
        ObjectNode result = base.deepCopy();
        mergeInto(result, patch, "");
        return result;
    }

    /**
     * Fails with an {@link IllegalArgumentException} naming the path (relative to {@code node}) unless every
     * {@code _append} in {@code node} is written {@code {"_append": [...]}} with nothing beside it, as the value of
     * a key of an object: at the top or inside a list it would never be applied (lists replace whole).
     */
    public static void checkAppends(JsonNode node) {
        if (node.isObject() && node.has(APPEND)) {
            throw new IllegalArgumentException("(top level): an _append is the value of the key whose list it adds to");
        }
        checkAppends(node, "", false);
    }

    /** {@code true} when {@code node} holds an {@code _append} at any depth. */
    public static boolean hasAppend(JsonNode node) {
        if (node.isObject() && node.has(APPEND)) return true;
        for (JsonNode child : node) {
            if (hasAppend(child)) return true;
        }
        return false;
    }

    /** A copy of {@code node} without {@code _comment} fields, at every depth. */
    public static JsonNode withoutComments(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = Json.MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (!field.getKey().equals(COMMENT)) {
                    copy.set(field.getKey(), withoutComments(field.getValue()));
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = Json.MAPPER.createArrayNode();
            node.forEach(element -> copy.add(withoutComments(element)));
            return copy;
        }
        return node.deepCopy();
    }

    private static void mergeInto(ObjectNode target, ObjectNode patch, String at) {
        for (Map.Entry<String, JsonNode> field : patch.properties()) {
            String key = field.getKey();
            JsonNode value = field.getValue();
            String path = at.isEmpty() ? key : at + "." + key;
            if (value.isNull()) {
                target.remove(key);
            } else if (value.isObject() && value.has(APPEND)) {
                target.set(key, appended(target.get(key), value, path));
            } else if (value.isObject()) {
                JsonNode existing = target.get(key);
                ObjectNode child = existing instanceof ObjectNode object ? object : target.putObject(key);
                mergeInto(child, (ObjectNode) value, path);
            } else {
                target.set(key, value.deepCopy());
            }
        }
    }

    private static ArrayNode appended(JsonNode inherited, JsonNode append, String path) {
        if (append.size() != 1 || !append.get(APPEND).isArray()) {
            throw new IllegalStateException(path + ": write {\"_append\": [ ... ]} with nothing beside it");
        }
        if (!(inherited instanceof ArrayNode list)) {
            throw new IllegalStateException(path + ": _append adds to the list an earlier layer gives this key, but "
                    + (inherited == null ? "no earlier layer gives it one"
                    : "the earlier value is " + inherited.getNodeType().name().toLowerCase(Locale.ROOT)
                    + ", not a list"));
        }
        ArrayNode result = list.deepCopy();
        for (JsonNode element : append.get(APPEND)) {
            JsonNode name = element.get("key");
            if (name != null && name.isTextual()) {
                for (JsonNode present : result) {
                    if (name.equals(present.get("key"))) {
                        throw new IllegalStateException(path + ": _append of key '" + name.asText()
                                + "', which the list already has; to change an element, give the whole list");
                    }
                }
            }
            result.add(element.deepCopy());
        }
        return result;
    }

    private static void checkAppends(JsonNode node, String at, boolean inList) {
        if (node.isObject()) {
            if (node.has(APPEND)) {
                if (inList) {
                    throw new IllegalArgumentException(at + ": an _append inside a list is never applied"
                            + " (lists replace whole)");
                }
                if (node.size() != 1 || !node.get(APPEND).isArray()) {
                    throw new IllegalArgumentException(at + ": write {\"_append\": [ ... ]} with nothing beside it");
                }
            }
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                checkAppends(field.getValue(), at.isEmpty() ? field.getKey() : at + "." + field.getKey(), inList);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                checkAppends(node.get(i), at + "[" + i + "]", true);
            }
        }
    }
}
