package com.ftk.tpip.mapping.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ftk.tpip.mapping.selector.DeterministicJsonPath;
import java.util.List;

final class JsonTargetWriter {
    private JsonTargetWriter() {}
    static JsonNode write(JsonNode root, DeterministicJsonPath path, JsonNode value) {
        return write(root == null ? MissingNode.getInstance() : root, path.tokens(), 0, value.deepCopy());
    }
    private static JsonNode write(JsonNode current, List<DeterministicJsonPath.Token> tokens,
            int position, JsonNode value) {
        if (position == tokens.size()) return value;
        var token = tokens.get(position);
        if (token instanceof DeterministicJsonPath.Property property) {
            ObjectNode object;
            if (current.isMissingNode() || current.isNull()) object = JsonNodeFactory.instance.objectNode();
            else if (current.isObject()) object = (ObjectNode) current;
            else throw new IllegalArgumentException("Target path collides with a non-object node at " + property.name());
            JsonNode child = object.get(property.name());
            object.set(property.name(), write(child == null ? MissingNode.getInstance() : child,
                    tokens, position + 1, value));
            return object;
        }
        DeterministicJsonPath.Index index = (DeterministicJsonPath.Index) token;
        ArrayNode array;
        if (current.isMissingNode() || current.isNull()) array = JsonNodeFactory.instance.arrayNode();
        else if (current.isArray()) array = (ArrayNode) current;
        else throw new IllegalArgumentException("Target path collides with a non-array node at index " + index.value());
        while (array.size() <= index.value()) array.addNull();
        array.set(index.value(), write(array.get(index.value()), tokens, position + 1, value));
        return array;
    }
}
