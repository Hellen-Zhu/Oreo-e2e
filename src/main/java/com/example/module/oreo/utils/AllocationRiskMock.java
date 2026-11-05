package com.example.module.oreo.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The mocked answer to {@code POST /trades/<id>/calculate-allocation-risk} for any number of legs. The request
 * carries the legs as parallel arrays ({@code notionalAmounts}, {@code premiumAmounts}, {@code counterpartyIds},
 * {@code portfolioIds}); the template response (setup {@code risk-calc-allocation-success-mock}) holds one leg in
 * {@code data.allocatedTradeRisks}, which is repeated for each leg of the request with that leg's values, its
 * {@code legNumber} and the {@code tradeId} {@code <id>-ALLOC-<n>}. Its {@code risk} and the trade-level risks stay as
 * in the template. So one template answers a 2-way and a 3-way allocation, equal or not, with the values the form
 * sent, and the confirmation dialog shows those.
 */
public final class AllocationRiskMock {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AllocationRiskMock() {
    }

    public static String answer(String tradeId, String templateResponse, String requestBody) {
        try {
            ObjectNode response = (ObjectNode) MAPPER.readTree(templateResponse);
            JsonNode request = MAPPER.readTree(requestBody == null ? "" : requestBody);
            JsonNode notionals = legValues(request, "notionalAmounts", -1);
            int legs = notionals.size();
            JsonNode premiums = legValues(request, "premiumAmounts", legs);
            JsonNode counterparties = legValues(request, "counterpartyIds", legs);
            JsonNode portfolios = legValues(request, "portfolioIds", legs);

            if (!(response.at("/data/allocatedTradeRisks") instanceof ArrayNode risks) || risks.isEmpty()) {
                throw new IllegalArgumentException("the template has no data.allocatedTradeRisks leg to repeat");
            }
            JsonNode legTemplate = risks.get(0);
            risks.removeAll();
            for (int i = 0; i < legs; i++) {
                ObjectNode leg = legTemplate.deepCopy();
                leg.put("legNumber", i + 1);
                leg.put("tradeId", tradeId + "-ALLOC-" + (i + 1));
                leg.set("notionalAmount", notionals.get(i));
                leg.set("premiumAmount", premiums.get(i));
                leg.set("counterpartyFmId", counterparties.get(i));
                leg.set("portfolioId", portfolios.get(i));
                risks.add(leg);
            }
            return MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("calculate-allocation-risk mock: not JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** The array {@code field} of the request, of {@code size} items unless {@code size} is negative. */
    private static JsonNode legValues(JsonNode request, String field, int size) {
        JsonNode values = request.path(field);
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalArgumentException("calculate-allocation-risk mock: request has no array '" + field
                    + "': " + request);
        }
        if (size >= 0 && values.size() != size) {
            throw new IllegalArgumentException("calculate-allocation-risk mock: '" + field + "' has " + values.size()
                    + " items, notionalAmounts " + size + ": " + request);
        }
        return values;
    }
}
