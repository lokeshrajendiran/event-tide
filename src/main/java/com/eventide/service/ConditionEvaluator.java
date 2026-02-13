package com.eventide.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Evaluates condition expressions against an event payload.
 *
 * Supported expressions:
 *   - "payload.field == 'value'"     → equality check
 *   - "payload.field != 'value'"     → inequality check
 *   - "payload.field > 100"          → numeric greater than
 *   - "payload.field < 100"          → numeric less than
 *   - null or empty string           → always matches (catch-all rule)
 *
 * HOW IT WORKS:
 *   1. Parse the condition string into: field, operator, expected value
 *   2. Extract the actual value from the payload map using the field path
 *   3. Compare actual vs expected using the operator
 *   4. Return true/false
 *
 * Example:
 *   condition = "payload.plan == 'enterprise'"
 *   payload   = {"plan": "enterprise", "name": "Lokesh"}
 *   → extracts field "plan", compares "enterprise" == "enterprise" → true
 */
@Component
public class ConditionEvaluator {

    /**
     * Evaluate a condition against a payload.
     * Returns true if the condition matches, false otherwise.
     */
    public boolean evaluate(String condition, Map<String, Object> payload) {
        // Null or empty condition = always matches (catch-all)
        if (condition == null || condition.isBlank()) {
            return true;
        }

        try {
            return parseAndEvaluate(condition.trim(), payload);
        } catch (Exception e) {
            // If condition can't be parsed, don't match (fail-safe)
            return false;
        }
    }

    private boolean parseAndEvaluate(String condition, Map<String, Object> payload) {
        // Determine the operator
        String operator;
        if (condition.contains("!=")) {
            operator = "!=";
        } else if (condition.contains("==")) {
            operator = "==";
        } else if (condition.contains(">=")) {
            operator = ">=";
        } else if (condition.contains("<=")) {
            operator = "<=";
        } else if (condition.contains(">")) {
            operator = ">";
        } else if (condition.contains("<")) {
            operator = "<";
        } else {
            return false; // Unknown operator
        }

        // Split into left (field path) and right (expected value)
        String[] parts = condition.split(operator, 2);
        if (parts.length != 2) return false;

        String fieldPath = parts[0].trim();
        String expectedRaw = parts[1].trim();

        // Resolve the field value from payload
        Object actualValue = resolveField(fieldPath, payload);
        if (actualValue == null) return false;

        // Clean the expected value (remove quotes if string)
        Object expectedValue = parseValue(expectedRaw);

        return compare(actualValue, expectedValue, operator);
    }

    /**
     * Resolves a dotted field path like "payload.plan" from the payload map.
     * Strips the "payload." prefix since we're already given the payload.
     */
    private Object resolveField(String fieldPath, Map<String, Object> payload) {
        // Remove "payload." prefix if present
        String path = fieldPath.startsWith("payload.")
                ? fieldPath.substring("payload.".length())
                : fieldPath;

        // Support nested fields: "address.city" → payload.get("address").get("city")
        String[] keys = path.split("\\.");
        Object current = payload;

        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Parse a raw value string into a typed value.
     * "'enterprise'" → "enterprise" (String)
     * "100"          → 100 (Number)
     * "true"         → true (Boolean)
     */
    private Object parseValue(String raw) {
        // String value: 'enterprise' or "enterprise"
        if ((raw.startsWith("'") && raw.endsWith("'"))
                || (raw.startsWith("\"") && raw.endsWith("\""))) {
            return raw.substring(1, raw.length() - 1);
        }
        // Boolean
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        // Numeric
        try {
            if (raw.contains(".")) return Double.parseDouble(raw);
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return raw; // Return as-is
        }
    }

    private boolean compare(Object actual, Object expected, String operator) {
        return switch (operator) {
            case "==" -> actual.toString().equals(expected.toString());
            case "!=" -> !actual.toString().equals(expected.toString());
            case ">", ">=", "<", "<=" -> compareNumeric(actual, expected, operator);
            default -> false;
        };
    }

    private boolean compareNumeric(Object actual, Object expected, String operator) {
        try {
            double a = Double.parseDouble(actual.toString());
            double e = Double.parseDouble(expected.toString());
            return switch (operator) {
                case ">" -> a > e;
                case ">=" -> a >= e;
                case "<" -> a < e;
                case "<=" -> a <= e;
                default -> false;
            };
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
