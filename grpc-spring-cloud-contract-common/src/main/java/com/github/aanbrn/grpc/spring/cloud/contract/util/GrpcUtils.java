package com.github.aanbrn.grpc.spring.cloud.contract.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.DynamicMessage.Builder;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

@UtilityClass
public class GrpcUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final JsonFactory jsonFactory = new JsonFactory();

    private static final Parser jsonFormatParser = JsonFormat.parser();

    public static String extractMethodName(@NonNull String uriPath) {
        checkArgument(uriPath.charAt(0) == '/', "Argument 'uriPath' must start with a slash");

        return uriPath.substring(1);
    }

    public static List<DynamicMessage> messagesFromJson(
            @NonNull String json, @NonNull Descriptor messageType) throws IOException {
        JsonNode rootNode = objectMapper.readTree(json);
        List<JsonNode> sources = new ArrayList<>();
        if (rootNode.isArray()) {
            Iterator<JsonNode> elementsIt = rootNode.elements();
            while (elementsIt.hasNext()) {
                JsonNode element = elementsIt.next();
                if (element.has("clientValue")
                        && element.has("serverValue")
                        && element.has("singleValue")
                        && element.get("singleValue").asBoolean()) {
                    sources.add(element.get("clientValue"));
                } else {
                    sources.add(element);
                }
            }
        } else if (rootNode.isObject()) {
            sources.add(rootNode);
        }
        return messagesFromJson(sources, messageType);
    }

    public static List<DynamicMessage> messagesFromJson(
            @NonNull List<JsonNode> jsonNodes, @NonNull Descriptor messageType) throws IOException {
        if (jsonNodes.isEmpty()) {
            return List.of();
        }
        List<DynamicMessage> result = new ArrayList<>(jsonNodes.size());
        for (JsonNode jsonNode : jsonNodes) {
            StringWriter writer = new StringWriter();
            objectMapper.writeTree(jsonFactory.createGenerator(writer), jsonNode);
            result.add(messageFromJson(writer.toString(), messageType));
        }
        return result;
    }

    public static DynamicMessage messageFromJson(String json, Descriptor messageType) throws IOException {
        Builder messageBuilder = DynamicMessage.newBuilder(messageType);
        jsonFormatParser.merge(json, messageBuilder);
        return messageBuilder.build();
    }

    public static List<Map<String, Object>> messagesAsList(@NonNull List<? extends Message> messages) {
        return messagesAsList(messages.iterator());
    }

    public static List<Map<String, Object>> messagesAsList(@NonNull Iterator<? extends Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        messages.forEachRemaining(message -> result.add(messageAsMap(message)));
        return result;
    }

    public static Map<String, Object> messageAsMap(@NonNull Message message) {
        Map<String, Object> result = new LinkedHashMap<>();
        message.getDescriptorForType()
               .getFields()
               .forEach(field -> {
                   Object fieldValue;
                   if (field.isRepeated()) {
                       if (field.isMapField()) {
                           fieldValue = mapFieldAsMap(message, field);
                       } else {
                           fieldValue = repeatedFieldAsList(message, field);
                       }
                   } else if (field.getJavaType() == JavaType.MESSAGE) {
                       fieldValue = messageAsMap((DynamicMessage) message.getField(field));
                   } else {
                       fieldValue = message.getField(field);
                   }
                   result.put(field.getName(), fieldValue);
               });
        return result;
    }

    public static List<Object> repeatedFieldAsList(@NonNull Message message, @NonNull FieldDescriptor field) {
        checkArgument(!field.isMapField(), "Argument 'field' cannot be a map field");

        int count = message.getRepeatedFieldCount(field);
        List<Object> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Object value = message.getRepeatedField(field, i);
            if (value instanceof Message) {
                result.add(messageAsMap((Message) value));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    public static Map<Object, Object> mapFieldAsMap(@NonNull Message message, @NonNull FieldDescriptor field) {
        checkArgument(field.isMapField(), "Argument 'field' must be a map field");

        int count = message.getRepeatedFieldCount(field);
        Map<Object, Object> result = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            MapEntry<?, ?> entry = (MapEntry<?, ?>) message.getRepeatedField(field, i);
            if (field.getJavaType() == JavaType.MESSAGE) {
                result.put(entry.getKey(), messageAsMap((DynamicMessage) entry.getValue()));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
