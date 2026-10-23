package com.example.module.oreo.testdata;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A setup payload from {@code data/setup/<name>.json}: a body sent to prepare a case, or a mocked answer.
 * {@link #request()} / {@link #response()} fail when the file has no such part; {@link #hasRequest()} /
 * {@link #hasResponse()} are for a caller to whom the part is optional.
 */
public final class SetupData {

    private final String name;
    private final String path;
    private final ObjectNode request;
    private final ObjectNode response;

    SetupData(String name, String path, ObjectNode request, ObjectNode response) {
        this.name = name;
        this.path = path;
        this.request = request == null ? null : request.deepCopy();
        this.response = response == null ? null : response.deepCopy();
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
