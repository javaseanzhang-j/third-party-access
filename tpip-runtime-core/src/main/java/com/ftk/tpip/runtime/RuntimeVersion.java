package com.ftk.tpip.runtime;

import java.util.Arrays;
import java.util.Objects;

public record RuntimeVersion(int major, int minor, int patch) implements Comparable<RuntimeVersion> {
    public RuntimeVersion {
        if (major < 0 || minor < 0 || patch < 0) throw new IllegalArgumentException("Version parts must not be negative");
    }

    public static RuntimeVersion parse(String value) {
        String[] parts = Objects.requireNonNull(value, "version must not be null").trim().split("\\.");
        if (parts.length < 1 || parts.length > 3 || Arrays.stream(parts).anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Invalid runtime version: " + value);
        }
        try {
            return new RuntimeVersion(Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
                    parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid runtime version: " + value, exception);
        }
    }

    public boolean satisfies(String expression) {
        if (expression == null || expression.isBlank()) return false;
        for (String constraint : expression.trim().split("\\s+")) {
            String operator;
            if (constraint.startsWith(">=") || constraint.startsWith("<=") || constraint.startsWith("==")) operator = constraint.substring(0, 2);
            else if (constraint.startsWith(">") || constraint.startsWith("<") || constraint.startsWith("=")) operator = constraint.substring(0, 1);
            else operator = "=";
            RuntimeVersion target = parse(constraint.substring(operator.equals("=") && !constraint.startsWith("=") ? 0 : operator.length()));
            int comparison = compareTo(target);
            boolean accepted = switch (operator) {
                case ">=" -> comparison >= 0;
                case ">" -> comparison > 0;
                case "<=" -> comparison <= 0;
                case "<" -> comparison < 0;
                case "=", "==" -> comparison == 0;
                default -> false;
            };
            if (!accepted) return false;
        }
        return true;
    }

    @Override
    public int compareTo(RuntimeVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        return result;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
