package com.eventide.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ConditionEvaluator — the expression parser that decides
 * whether an event payload matches a rule's condition.
 *
 * We test every operator, edge case, and failure mode because this is
 * the decision-making core of the engine. A bug here means wrong actions
 * get dispatched to real systems.
 */
class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator();
    }

    // --- Catch-all rules (null/empty condition = always match) ---

    @Nested
    @DisplayName("Catch-all conditions (null or empty)")
    class CatchAllTests {

        @Test
        @DisplayName("null condition should always match")
        void nullCondition_shouldMatch() {
            assertTrue(evaluator.evaluate(null, Map.of("key", "value")));
        }

        @Test
        @DisplayName("empty string condition should always match")
        void emptyCondition_shouldMatch() {
            assertTrue(evaluator.evaluate("", Map.of("key", "value")));
        }

        @Test
        @DisplayName("blank string condition should always match")
        void blankCondition_shouldMatch() {
            assertTrue(evaluator.evaluate("   ", Map.of("key", "value")));
        }
    }

    // --- Equality operator (==) ---

    @Nested
    @DisplayName("Equality operator (==)")
    class EqualityTests {

        @Test
        @DisplayName("should match when string values are equal")
        void stringEquality_shouldMatch() {
            Map<String, Object> payload = Map.of("plan", "enterprise");
            assertTrue(evaluator.evaluate("payload.plan == 'enterprise'", payload));
        }

        @Test
        @DisplayName("should NOT match when string values differ")
        void stringEquality_shouldNotMatch() {
            Map<String, Object> payload = Map.of("plan", "standard");
            assertFalse(evaluator.evaluate("payload.plan == 'enterprise'", payload));
        }

        @Test
        @DisplayName("should match numeric equality")
        void numericEquality_shouldMatch() {
            Map<String, Object> payload = Map.of("quantity", 100);
            assertTrue(evaluator.evaluate("payload.quantity == 100", payload));
        }

        @Test
        @DisplayName("should work without 'payload.' prefix")
        void withoutPrefix_shouldMatch() {
            Map<String, Object> payload = Map.of("status", "active");
            assertTrue(evaluator.evaluate("status == 'active'", payload));
        }
    }

    // --- Inequality operator (!=) ---

    @Nested
    @DisplayName("Inequality operator (!=)")
    class InequalityTests {

        @Test
        @DisplayName("should match when values are different")
        void notEqual_shouldMatch() {
            Map<String, Object> payload = Map.of("plan", "standard");
            assertTrue(evaluator.evaluate("payload.plan != 'enterprise'", payload));
        }

        @Test
        @DisplayName("should NOT match when values are the same")
        void notEqual_shouldNotMatch() {
            Map<String, Object> payload = Map.of("plan", "enterprise");
            assertFalse(evaluator.evaluate("payload.plan != 'enterprise'", payload));
        }
    }

    // --- Numeric comparison operators (>, <, >=, <=) ---

    @Nested
    @DisplayName("Numeric comparisons")
    class NumericTests {

        @Test
        @DisplayName("greater than should match when actual > expected")
        void greaterThan_shouldMatch() {
            Map<String, Object> payload = Map.of("amount", 500);
            assertTrue(evaluator.evaluate("payload.amount > 100", payload));
        }

        @Test
        @DisplayName("greater than should NOT match when actual <= expected")
        void greaterThan_shouldNotMatch() {
            Map<String, Object> payload = Map.of("amount", 50);
            assertFalse(evaluator.evaluate("payload.amount > 100", payload));
        }

        @Test
        @DisplayName("less than should match when actual < expected")
        void lessThan_shouldMatch() {
            Map<String, Object> payload = Map.of("age", 15);
            assertTrue(evaluator.evaluate("payload.age < 18", payload));
        }

        @Test
        @DisplayName("greater than or equal should match on boundary")
        void greaterThanOrEqual_shouldMatchBoundary() {
            Map<String, Object> payload = Map.of("score", 100);
            assertTrue(evaluator.evaluate("payload.score >= 100", payload));
        }

        @Test
        @DisplayName("less than or equal should match on boundary")
        void lessThanOrEqual_shouldMatchBoundary() {
            Map<String, Object> payload = Map.of("score", 100);
            assertTrue(evaluator.evaluate("payload.score <= 100", payload));
        }
    }

    // --- Nested field access ---

    @Nested
    @DisplayName("Nested field access")
    class NestedFieldTests {

        @Test
        @DisplayName("should resolve nested fields like payload.address.city")
        void nestedField_shouldResolve() {
            Map<String, Object> address = Map.of("city", "Chennai");
            Map<String, Object> payload = Map.of("address", address);

            assertTrue(evaluator.evaluate("payload.address.city == 'Chennai'", payload));
        }

        @Test
        @DisplayName("should return false for non-existent nested path")
        void missingNestedField_shouldNotMatch() {
            Map<String, Object> payload = Map.of("name", "Lokesh");
            assertFalse(evaluator.evaluate("payload.address.city == 'Chennai'", payload));
        }
    }

    // --- Edge cases and failure modes ---

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("missing field in payload should return false")
        void missingField_shouldNotMatch() {
            Map<String, Object> payload = Map.of("name", "Lokesh");
            assertFalse(evaluator.evaluate("payload.plan == 'enterprise'", payload));
        }

        @Test
        @DisplayName("invalid expression should return false (fail-safe)")
        void invalidExpression_shouldNotMatch() {
            Map<String, Object> payload = Map.of("name", "Lokesh");
            assertFalse(evaluator.evaluate("this is not a valid expression", payload));
        }

        @Test
        @DisplayName("empty payload should return false for any condition")
        void emptyPayload_shouldNotMatch() {
            assertFalse(evaluator.evaluate("payload.plan == 'enterprise'", new HashMap<>()));
        }

        @Test
        @DisplayName("double-quoted strings should work")
        void doubleQuotedString_shouldMatch() {
            Map<String, Object> payload = Map.of("plan", "enterprise");
            assertTrue(evaluator.evaluate("payload.plan == \"enterprise\"", payload));
        }
    }
}
