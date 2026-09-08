package com.campus.mahjong.infrastructure.network.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/** 4 字节长度前缀 + UTF-8 JSON，拒绝过大的网络帧。 */
public final class JsonMessageCodec {
    public static final int MAX_FRAME_BYTES = 64 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new Jdk8Module());

    public MessageEnvelope read(DataInputStream input) throws IOException {
        int length;
        try {
            length = input.readInt();
        } catch (EOFException exception) {
            throw exception;
        }
        if (length <= 0 || length > MAX_FRAME_BYTES) throw new IOException("非法消息长度: " + length);
        return JSON.readValue(input.readNBytes(length), MessageEnvelope.class);
    }

    public void write(DataOutputStream output, MessageEnvelope envelope) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(envelope);
        if (bytes.length > MAX_FRAME_BYTES) throw new IOException("消息超过 64 KB");
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    public static JsonNode tree(Object value) { return JSON.valueToTree(value); }

    public static <T> T value(JsonNode node, Class<T> type) {
        return JSON.convertValue(node, type);
    }
}
