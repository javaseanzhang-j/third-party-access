package com.ftk.tpip.mapping.selector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.*;

/** Parser and reader for the bounded, deterministic TPIP JSONPath v0.1 subset. */
public final class DeterministicJsonPath {
    public sealed interface Token permits Property, Index {}
    public record Property(String name) implements Token {}
    public record Index(int value) implements Token {}
    private static final int MAX_DEPTH = 64;
    private static final int MAX_INDEX = 10_000;
    private final String expression;
    private final List<Token> tokens;

    private DeterministicJsonPath(String expression, List<Token> tokens) {
        this.expression = expression; this.tokens = List.copyOf(tokens);
    }
    public static DeterministicJsonPath parse(String expression) {
        if (expression == null || expression.isBlank() || expression.length() > 1000 || expression.charAt(0) != '$')
            throw new IllegalArgumentException("JSONPath must start with $ and contain at most 1000 characters");
        List<Token> tokens = new ArrayList<>();
        int i = 1;
        while (i < expression.length()) {
            if (tokens.size() >= MAX_DEPTH) throw new IllegalArgumentException("JSONPath exceeds maximum depth " + MAX_DEPTH);
            char marker = expression.charAt(i);
            if (marker == '.') {
                int start = ++i;
                if (start >= expression.length() || !identifierStart(expression.charAt(start)))
                    throw new IllegalArgumentException("Invalid property segment at position " + start);
                i++;
                while (i < expression.length() && identifierPart(expression.charAt(i))) i++;
                tokens.add(new Property(expression.substring(start, i)));
            } else if (marker == '[') {
                int start = ++i;
                while (i < expression.length() && Character.isDigit(expression.charAt(i))) i++;
                if (start == i || i >= expression.length() || expression.charAt(i) != ']')
                    throw new IllegalArgumentException("Invalid array index at position " + start);
                String raw = expression.substring(start, i++);
                if (raw.length() > 1 && raw.charAt(0) == '0') throw new IllegalArgumentException("Array index must not contain leading zeros");
                long value;
                try { value = Long.parseLong(raw); } catch (NumberFormatException e) { throw new IllegalArgumentException("Array index is too large"); }
                if (value > MAX_INDEX) throw new IllegalArgumentException("Array index exceeds maximum " + MAX_INDEX);
                tokens.add(new Index((int) value));
            } else {
                throw new IllegalArgumentException("Unsupported JSONPath token at position " + i);
            }
        }
        return new DeterministicJsonPath(expression, tokens);
    }
    public JsonNode read(JsonNode source) {
        if (source == null) return MissingNode.getInstance();
        JsonNode current = source;
        for (Token token : tokens) {
            if (token instanceof Property property) {
                if (!current.isObject() || !current.has(property.name())) return MissingNode.getInstance();
                current = current.get(property.name());
            } else if (token instanceof Index index) {
                if (!current.isArray() || index.value() >= current.size()) return MissingNode.getInstance();
                current = current.get(index.value());
            }
            if (current == null) return MissingNode.getInstance();
        }
        return current;
    }
    public String expression() { return expression; }
    public List<Token> tokens() { return tokens; }
    private static boolean identifierStart(char value) { return Character.isLetter(value) || value == '_'; }
    private static boolean identifierPart(char value) { return Character.isLetterOrDigit(value) || value == '_' || value == '-'; }
}
