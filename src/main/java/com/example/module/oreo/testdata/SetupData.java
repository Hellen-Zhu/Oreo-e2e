package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A setup payload from {@code data/setup/<name>.json}: a body sent to prepare a case, or a mocked answer.
 * {@link #request()} / {@link #response()} fail when the file has no such part; {@link #hasRequest()} /
 * {@link #hasResponse()} are for a caller to whom the part is optional. Its {@code byProduct} (composition -> request)
 * is what {@link #forProduct} merges over the request for a product.
 */
public final class SetupData {

    private final String name;
    private final String path;
    private final ObjectNode request;
    private final ObjectNode response;
    private final Map<String, ObjectNode> byProduct;

    SetupData(String name, String path, ObjectNode request, ObjectNode response) {
        this(name, path, request, response, Map.of());
    }

    SetupData(String name, String path, ObjectNode request, ObjectNode response, Map<String, ObjectNode> byProduct) {
        this.name = name;
        this.path = path;
        this.request = request == null ? null : request.deepCopy();
        this.response = response == null ? null : response.deepCopy();
        Map<String, ObjectNode> copy = new LinkedHashMap<>();
        byProduct.forEach((product, over) -> copy.put(product, over.deepCopy()));
        this.byProduct = Map.copyOf(copy);
    }

    /**
     * This setup data for composition {@code product} ({@code FX_TRF}): its {@code byProduct} request of that product
     * merged over the request; this setup data itself when it has none, or when {@code product} is null.
     */
    public SetupData forProduct(String product) {
        ObjectNode over = product == null ? null : byProduct.get(product);
        if (over == null) return this;
        return new SetupData(name, path, request == null ? over : JsonLayers.merge(request, over), response);
    }

    public String name() { return name; }

    /** Compact JSON of the request, {@code ${date:...}} filled in for today. */
    public String request() { return Json.compact(DataPlaceholders.resolve(required(request, "request"))); }

    /** Compact JSON of the response, {@code ${date:...}} filled in for today. */
    public String response() { return Json.compact(DataPlaceholders.resolve(required(response, "response"))); }

    public boolean hasRequest() { return request != null; }

    public boolean hasResponse() { return response != null; }

    ObjectNode requestNode() { return request; }

    ObjectNode responseNode() { return response; }

    Map<String, ObjectNode> byProductNodes() { return byProduct; }

    /** {@code child} merged over {@code parent}: a product's request along an {@code extends} chain. */
    static ObjectNode over(ObjectNode parent, ObjectNode child) {
        return JsonLayers.merge(parent, child);
    }

    private ObjectNode required(ObjectNode part, String partName) {
        if (part == null) {
            throw new IllegalStateException("[TestData] Setup data '" + name + "' has no " + partName
                    + " (" + path + ", with its extends chain)");
        }
        return part;
    }

    @Override
    public String toString() {
        return "SetupData{" + name + ", request=" + hasRequest() + ", response=" + hasResponse() + '}';
    }
}
