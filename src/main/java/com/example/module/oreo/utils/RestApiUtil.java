package com.example.module.oreo.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.genie.model.GenieScenario;
import com.example.genie.module.rest.RestModule;
import com.example.genie.module.rest.service.RequestResponseHelper;
import com.example.module.oreo.config.TestUser;
import cucumber.api.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.UUID;

public final class RestApiUtil {

    private static final Logger log = LoggerFactory.getLogger(RestApiUtil.class);
    private static final ObjectMapper API_MAPPER = new ObjectMapper();

    /** Required on every OREO API request: a new UUID per request. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    /** The acting user of an OREO API request: an e-mail. */
    public static final String USER_HEADER = "X-User-Id";

    private RestApiUtil() {
        // Utility class
    }

    public static RequestResponseHelper helperFromScenario(Scenario scenario) {
        GenieScenario genieScenario = (GenieScenario) scenario;
        RequestResponseHelper helper = genieScenario.getAttribute(RestModule.REQUEST_RESPONSE_HELPER);
        if (helper == null) {
            throw new IllegalStateException(
                    "[RestApiUtil] RequestResponseHelper not initialized. Ensure REST module is active for this scenario.");
        }
        return helper;
    }

    public static JsonNode get(RequestResponseHelper helper, String pathOrUrl, String userId) {
        String target = normalizeTarget(pathOrUrl);
        log.info("[RestApiUtil] GET start: target='{}', userId='{}'", target, userId);

        String requestId = prepareRequest(helper, userId, null);
        return send(helper, "GET " + target, requestId, () -> helper.get(target));
    }

    public static JsonNode post(RequestResponseHelper helper, String pathOrUrl, String userId, String jsonBody) {
        String target = normalizeTarget(pathOrUrl);
        int bodyLength = jsonBody == null ? 0 : jsonBody.length();
        log.info("[RestApiUtil] POST start (json): target='{}', userId='{}', bodyLength={}", target, userId, bodyLength);

        String requestId = prepareRequest(helper, userId, jsonBody);
        return send(helper, "POST " + target, requestId, () -> helper.post(target));
    }

    public static JsonNode post(RequestResponseHelper helper, String pathOrUrl, String userId) {
        String target = normalizeTarget(pathOrUrl);
        log.info("[RestApiUtil] POST start (no-body): target='{}', userId='{}'", target, userId);

        String requestId = prepareRequest(helper, userId, null);
        return send(helper, "POST " + target, requestId, () -> helper.post(target));
    }

    public static JsonNode put(RequestResponseHelper helper, String pathOrUrl, String userId, String jsonBody) {
        String target = normalizeTarget(pathOrUrl);
        int bodyLength = jsonBody == null ? 0 : jsonBody.length();
        log.info("[RestApiUtil] PUT start: target='{}', userId='{}', bodyLength={}", target, userId, bodyLength);

        String requestId = prepareRequest(helper, userId, jsonBody);
        return send(helper, "PUT " + target, requestId, () -> helper.put(target));
    }

    public static JsonNode delete(RequestResponseHelper helper, String pathOrUrl, String userId) {
        String target = normalizeTarget(pathOrUrl);
        log.info("[RestApiUtil] DELETE start: target='{}', userId='{}'", target, userId);

        String requestId = prepareRequest(helper, userId, null);
        return send(helper, "DELETE " + target, requestId, () -> helper.delete(target));
    }

    public static JsonNode postMultipart(
            RequestResponseHelper helper,
            String pathOrUrl,
            String userId,
            String jsonPartName,
            String jsonPartValue,
            String filePartName,
            Path filePath
    ) {
        String target = normalizeTarget(pathOrUrl);
        log.info("[RestApiUtil] POST start (multipart): target='{}', userId='{}', jsonPart='{}', filePart='{}', file='{}'",
                target, userId, jsonPartName, filePartName, filePath);

        helper.newRequest();
        setUserHeader(helper, userId);
        String requestId = setNewRequestId(helper);
        helper.request().multiPart(jsonPartName, jsonPartValue, "application/json");
        helper.request().multiPart(filePartName, filePath.toFile());
        return send(helper, "POST " + target, requestId, () -> helper.post(target));
    }

    /** Starts a JSON request as {@code userId} and returns its request ID. */
    private static String prepareRequest(RequestResponseHelper helper, String userId, String jsonBody) {
        helper.newRequest();
        setUserHeader(helper, userId);
        String requestId = setNewRequestId(helper);
        helper.request().header("Content-Type", "application/json");
        if (jsonBody != null) {
            helper.request().body(jsonBody);
        }
        return requestId;
    }

    /**
     * Sends the request and parses its response. Every failure names the request ID, so it can be traced in the
     * server logs: a non-2xx response, an unparsable body, and an exception before any response (a refused connection,
     * a terminated TLS handshake, a timeout).
     */
    private static JsonNode send(RequestResponseHelper helper, String call, String requestId, Runnable request) {
        String label = call + " (" + REQUEST_ID_HEADER + " " + requestId + ")";
        try {
            request.run();
        } catch (Exception e) {
            log.error("[RestApiUtil] No response: label='{}'", label, e);
            throw new AssertionError(label + " got no response: " + e, e);
        }
        return assertAndParse(helper, label);
    }

    /**
     * Sets {@link #USER_HEADER} on the request being built and returns its value: the e-mail of a {@link TestUser}
     * role ({@code maker}), or an e-mail as given. The snippet step {@code set the user header for '...'} calls it too.
     */
    public static String setUserHeader(RequestResponseHelper helper, String roleOrEmail) {
        String userId = TestUser.userId(roleOrEmail);
        helper.request().header(USER_HEADER, userId);
        return userId;
    }

    /**
     * Sets {@link #REQUEST_ID_HEADER} on the request being built to a new UUID and returns it. Call once per request,
     * after {@code helper.newRequest()}; the snippet step {@code set a new request id header} calls it too.
     */
    public static String setNewRequestId(RequestResponseHelper helper) {
        String requestId = UUID.randomUUID().toString();
        helper.request().header(REQUEST_ID_HEADER, requestId);
        log.info("[RestApiUtil] {}: {}", REQUEST_ID_HEADER, requestId);
        return requestId;
    }

    private static String normalizeTarget(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            throw new IllegalArgumentException("pathOrUrl must not be null/blank");
        }

        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }

        return pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl;
    }

    private static JsonNode assertAndParse(RequestResponseHelper helper, String label) {
        int statusCode = helper.response().extract().statusCode();
        String responseText = helper.response().extract().asString();

        if (statusCode < 200 || statusCode >= 300) {
            log.error("[RestApiUtil] Non-2xx response: label='{}', status={}, body={}",
                    label, statusCode, responseText);
            throw new AssertionError(label + " returned HTTP " + statusCode + "\n" + responseText);
        }

        if (responseText == null || responseText.isBlank()) {
            log.debug("[RestApiUtil] Empty response body: label='{}'", label);
            return API_MAPPER.createObjectNode();
        }

        try {
            JsonNode parsed = API_MAPPER.readTree(responseText);
            log.debug("[RestApiUtil] Parsed JSON response successfully: label='{}'", label);
            return parsed;
        } catch (Exception e) {
            log.error("[RestApiUtil] Failed to parse response as JSON: label='{}', body={}",
                    label, responseText, e);
            throw new RuntimeException("Failed to parse API response as JSON: " + label, e);
        }
    }
}
