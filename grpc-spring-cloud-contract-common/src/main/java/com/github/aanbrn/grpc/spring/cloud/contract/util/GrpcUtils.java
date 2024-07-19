package com.github.aanbrn.grpc.spring.cloud.contract.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;

@UtilityClass
public class GrpcUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final JsonFactory jsonFactory = new JsonFactory();

    private static final Parser jsonFormatParser = JsonFormat.parser();

    public static String extractMethodName(@NonNull final String uriPath) {
        checkArgument(uriPath.charAt(0) == '/', "Argument 'uriPath' must start with a slash");

        return uriPath.substring(1);
    }

    public static List<DynamicMessage> messagesFromJson(
            @NonNull final String json, @NonNull final Descriptor messageType) throws IOException {
        val rootNode = objectMapper.readTree(json);
        val sources = new ArrayList<JsonNode>();
        if (rootNode.isArray()) {
            val elementsIt = rootNode.elements();
            while (elementsIt.hasNext()) {
                val element = elementsIt.next();
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
            @NonNull final List<JsonNode> jsonNodes, @NonNull final Descriptor messageType) throws IOException {
        if (jsonNodes.isEmpty()) {
            return List.of();
        }
        val result = new ArrayList<DynamicMessage>(jsonNodes.size());
        for (val jsonNode : jsonNodes) {
            StringWriter writer = new StringWriter();
            objectMapper.writeTree(jsonFactory.createGenerator(writer), jsonNode);
            result.add(messageFromJson(writer.toString(), messageType));
        }
        return result;
    }

    public static DynamicMessage messageFromJson(
            @NonNull final String json, @NonNull final Descriptor messageType) throws IOException {
        val messageBuilder = DynamicMessage.newBuilder(messageType);
        jsonFormatParser.merge(json, messageBuilder);
        return messageBuilder.build();
    }

    public static List<Map<String, Object>> messagesAsList(@NonNull final List<? extends Message> messages) {
        return messagesAsList(messages.iterator());
    }

    public static List<Map<String, Object>> messagesAsList(@NonNull final Iterator<? extends Message> messages) {
        val result = new ArrayList<Map<String, Object>>();
        messages.forEachRemaining(message -> result.add(messageAsMap(message)));
        return result;
    }

    public static Map<String, Object> messageAsMap(@NonNull final Message message) {
        val result = new LinkedHashMap<String, Object>();
        for (val field : message.getDescriptorForType().getFields()) {
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
        }
        return result;
    }

    public static List<Object> repeatedFieldAsList(
            @NonNull final Message message, @NonNull final FieldDescriptor field) {
        checkArgument(!field.isMapField(), "Argument 'field' cannot be a map field");

        val count = message.getRepeatedFieldCount(field);
        val result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            val value = message.getRepeatedField(field, i);
            if (value instanceof Message) {
                result.add(messageAsMap((Message) value));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    public static Map<Object, Object> mapFieldAsMap(
            @NonNull final Message message, @NonNull final FieldDescriptor field) {
        checkArgument(field.isMapField(), "Argument 'field' must be a map field");

        val count = message.getRepeatedFieldCount(field);
        val result = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            val entry = (MapEntry<?, ?>) message.getRepeatedField(field, i);
            if (field.getJavaType() == JavaType.MESSAGE) {
                result.put(entry.getKey(), messageAsMap((DynamicMessage) entry.getValue()));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
