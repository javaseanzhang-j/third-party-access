package com.ftk.tpip.policy.compiler;

import java.util.*;
import java.util.regex.*;

final class SafeConditionProfile {
    private static final Pattern TOKEN = Pattern.compile("\\s*(?:(&&|\\|\\||==|!=|>=|<=|>|<|!|\\(|\\))|([A-Za-z_][A-Za-z0-9_.-]*)|(-?\\d+(?:\\.\\d+)?)|('(?:[^'\\\\]|\\\\.)*')|(true|false|null))");
    private static final Set<String> ROOTS = Set.of("canonical", "provider", "context", "transport", "outcome");
    private SafeConditionProfile() {}
    static boolean valid(String expression) {
        if (expression == null || expression.isBlank()) return true;
        if (expression.length() > 1000 || expression.contains("${") || expression.contains(";") || expression.contains("[") || expression.contains("{")) return false;
        Matcher matcher = TOKEN.matcher(expression); int end = 0; int balance = 0; boolean hasOperand = false;
        while (matcher.find()) {
            if (matcher.start() != end && !expression.substring(end, matcher.start()).isBlank()) return false;
            String operator = matcher.group(1); String identifier = matcher.group(2);
            if ("(".equals(operator)) balance++; else if (")".equals(operator) && --balance < 0) return false;
            if (identifier != null) {
                if (!Set.of("true", "false", "null").contains(identifier)) {
                    String root = identifier.contains(".") ? identifier.substring(0, identifier.indexOf('.')) : identifier;
                    if (!ROOTS.contains(root)) return false;
                }
                hasOperand = true;
            }
            if (matcher.group(3) != null || matcher.group(4) != null || matcher.group(5) != null) hasOperand = true;
            end = matcher.end();
        }
        return hasOperand && balance == 0 && expression.substring(end).isBlank();
    }
}
