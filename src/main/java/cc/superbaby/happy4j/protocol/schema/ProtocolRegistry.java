package cc.superbaby.happy4j.protocol.schema;

import cc.superbaby.happy4j.protocol.wire.ProtocolException;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;

import cc.superbaby.happy4j.protocol.wire.ProtocolException;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Dynamic access to every recovered managed and native message without a generated switch.
 */
public final class ProtocolRegistry {
    public static final String DEFAULT_DESCRIPTOR_RESOURCE = "/protocol/hlddz.desc";

    private final Map<String, Descriptors.Descriptor> messages;
    private final Map<String, List<Descriptors.Descriptor>> messagesBySimpleName;
    private final Map<String, Descriptors.EnumDescriptor> enums;

    private ProtocolRegistry(
            Map<String, Descriptors.Descriptor> messages,
            Map<String, List<Descriptors.Descriptor>> messagesBySimpleName,
            Map<String, Descriptors.EnumDescriptor> enums) {
        this.messages = Map.copyOf(messages);
        this.messagesBySimpleName = messagesBySimpleName.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
        this.enums = Map.copyOf(enums);
    }

    public static ProtocolRegistry loadDefault() {
        try (InputStream input = ProtocolRegistry.class.getResourceAsStream(DEFAULT_DESCRIPTOR_RESOURCE)) {
            if (input == null) {
                throw new ProtocolException("missing descriptor resource " + DEFAULT_DESCRIPTOR_RESOURCE);
            }
            return load(input);
        } catch (IOException e) {
            throw new ProtocolException("failed to read protocol descriptors", e);
        }
    }

    public static ProtocolRegistry load(InputStream input) throws IOException {
        FileDescriptorSet set = FileDescriptorSet.parseFrom(input);
        Map<String, FileDescriptorProto> pending = set.getFileList().stream()
                .collect(Collectors.toMap(FileDescriptorProto::getName, proto -> proto,
                        (left, right) -> left, LinkedHashMap::new));
        Map<String, Descriptors.FileDescriptor> built = new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            boolean progress = false;
            for (FileDescriptorProto proto : new ArrayList<>(pending.values())) {
                if (!built.keySet().containsAll(proto.getDependencyList())) {
                    continue;
                }
                Descriptors.FileDescriptor[] dependencies = proto.getDependencyList().stream()
                        .map(built::get)
                        .toArray(Descriptors.FileDescriptor[]::new);
                try {
                    built.put(proto.getName(), Descriptors.FileDescriptor.buildFrom(proto, dependencies));
                } catch (Descriptors.DescriptorValidationException e) {
                    throw new ProtocolException("invalid descriptor " + proto.getName(), e);
                }
                pending.remove(proto.getName());
                progress = true;
            }
            if (!progress) {
                throw new ProtocolException("descriptor dependency cycle or missing dependency: "
                        + pending.keySet());
            }
        }

        Map<String, Descriptors.Descriptor> messages = new LinkedHashMap<>();
        Map<String, List<Descriptors.Descriptor>> simple = new LinkedHashMap<>();
        Map<String, Descriptors.EnumDescriptor> enums = new LinkedHashMap<>();
        for (Descriptors.FileDescriptor file : built.values()) {
            for (Descriptors.Descriptor message : file.getMessageTypes()) {
                addMessage(message, messages, simple, enums);
            }
            for (Descriptors.EnumDescriptor descriptor : file.getEnumTypes()) {
                enums.put(descriptor.getFullName(), descriptor);
            }
        }
        return new ProtocolRegistry(messages, simple, enums);
    }

    public Collection<String> messageNames() {
        return messages.keySet().stream().sorted().toList();
    }

    public int messageCount() {
        return messages.size();
    }

    public Collection<String> enumNames() {
        return enums.keySet().stream().sorted().toList();
    }

    public int enumCount() {
        return enums.size();
    }

    public List<FieldInfo> describe(String messageName) {
        return message(messageName).getFields().stream()
                .sorted(Comparator.comparingInt(Descriptors.FieldDescriptor::getNumber))
                .map(field -> new FieldInfo(
                        field.getNumber(),
                        field.getName(),
                        field.getJsonName(),
                        field.getType().name(),
                        field.isRepeated(),
                        field.isMapField(),
                        field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE
                                ? field.getMessageType().getFullName() : null,
                        field.getJavaType() == Descriptors.FieldDescriptor.JavaType.ENUM
                                ? field.getEnumType().getFullName() : null))
                .toList();
    }

    public Descriptors.Descriptor message(String name) {
        Objects.requireNonNull(name, "name");
        Descriptors.Descriptor exact = messages.get(name);
        if (exact != null) {
            return exact;
        }
        List<Descriptors.Descriptor> candidates = messagesBySimpleName.getOrDefault(name, List.of());
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (candidates.isEmpty()) {
            throw new ProtocolException("unknown message type: " + name);
        }
        throw new ProtocolException("ambiguous message type " + name + ": "
                + candidates.stream().map(Descriptors.Descriptor::getFullName).sorted().toList());
    }

    public Descriptors.EnumDescriptor enumType(String fullName) {
        Descriptors.EnumDescriptor descriptor = enums.get(fullName);
        if (descriptor == null) {
            throw new ProtocolException("unknown enum type: " + fullName);
        }
        return descriptor;
    }

    public List<String> enumValuesForNumber(int number) {
        return enums.values().stream()
                .flatMap(type -> type.getValues().stream()
                        .filter(value -> value.getNumber() == number)
                        .map(value -> type.getFullName() + '.' + value.getName()))
                .sorted()
                .toList();
    }

    public byte[] encodeJson(String messageName, String json) {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(message(messageName));
        try {
            JsonFormat.parser().merge(json, builder);
            return builder.build().toByteArray();
        } catch (InvalidProtocolBufferException e) {
            throw new ProtocolException("invalid JSON for " + messageName, e);
        }
    }

    public DynamicMessage decode(String messageName, byte[] payload) {
        try {
            return DynamicMessage.parseFrom(message(messageName), payload);
        } catch (InvalidProtocolBufferException e) {
            throw new ProtocolException("invalid protobuf payload for " + messageName, e);
        }
    }

    public String decodeJson(String messageName, byte[] payload) {
        try {
            return JsonFormat.printer().alwaysPrintFieldsWithNoPresence().print(decode(messageName, payload));
        } catch (InvalidProtocolBufferException e) {
            throw new ProtocolException("failed to render " + messageName + " as JSON", e);
        }
    }

    private static void addMessage(
            Descriptors.Descriptor message,
            Map<String, Descriptors.Descriptor> messages,
            Map<String, List<Descriptors.Descriptor>> simple,
            Map<String, Descriptors.EnumDescriptor> enums) {
        messages.put(message.getFullName(), message);
        simple.computeIfAbsent(message.getName(), ignored -> new ArrayList<>()).add(message);
        for (Descriptors.EnumDescriptor descriptor : message.getEnumTypes()) {
            enums.put(descriptor.getFullName(), descriptor);
        }
        for (Descriptors.Descriptor nested : message.getNestedTypes()) {
            addMessage(nested, messages, simple, enums);
        }
    }

    public record FieldInfo(
            int number,
            String name,
            String jsonName,
            String wireType,
            boolean repeated,
            boolean map,
            String messageType,
            String enumType) {
    }
}
