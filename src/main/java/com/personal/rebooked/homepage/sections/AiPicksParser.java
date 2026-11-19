package com.personal.rebooked.homepage.sections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.rebooked.homepage.dto.PickRef;

import java.util.*;

/**
 * Parses the model's answer into picks (§24e): a JSON array of {id, reason}, optionally wrapped in a code fence or
 * prose. Ids not in the candidate pool and repeats are dropped, reasons trimmed to 90 characters. Fewer than 4 valid
 * picks is a failure.
 */
public final class AiPicksParser {
    public static final int REASON_MAX = 90;
    public static final int MIN_VALID = 4;
    public static final int MAX_PICKS = 8;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiPicksParser() {}

    public static class InvalidResponse extends RuntimeException {
        public InvalidResponse(String message) {
            super(message);
        }
    }

    public static List<PickRef> parse(String response, Set<String> pool) {
        if (response == null || response.isBlank()) throw new InvalidResponse("Empty response");
        int open = response.indexOf('[');
        int close = response.lastIndexOf(']');
        if (open < 0 || close <= open) throw new InvalidResponse("No JSON array in the response");
        JsonNode root;
        try {
            root = MAPPER.readTree(response.substring(open, close + 1));
        } catch (Exception e) {
            throw new InvalidResponse("Response isn't valid JSON");
        }
        if (!root.isArray()) throw new InvalidResponse("Response isn't a JSON array");

        Map<String, PickRef> picks = new LinkedHashMap<>();
        for (JsonNode node : root) {
            if (picks.size() >= MAX_PICKS) break;
            String id = node.isTextual() ? node.asText() : node.path("id").asText(null);
            if (id == null || !pool.contains(id.trim()) || picks.containsKey(id.trim())) continue;
            picks.put(id.trim(), new PickRef(id.trim(), reason(node.path("reason").asText(""))));
        }
        if (picks.size() < MIN_VALID) {
            throw new InvalidResponse("Only " + picks.size() + " valid picks (need " + MIN_VALID + ")");
        }
        return new ArrayList<>(picks.values());
    }

    static String reason(String raw) {
        String reason = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (reason.length() <= REASON_MAX) return reason;
        return reason.substring(0, REASON_MAX).trim();
    }
}
