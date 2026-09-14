package com.campus.mahjong.infrastructure.network.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

/** 每个 TCP 帧只包含一个协议信封。 */
public record MessageEnvelope(int protocolVersion, String requestId, MessageType messageType,
                              String roomId, String playerId, long expectedRevision,
                              JsonNode payload) {
    public static final int CURRENT_VERSION = 5;

    public MessageEnvelope {
        if (protocolVersion <= 0) throw new IllegalArgumentException("protocolVersion");
        if (requestId == null) requestId = "";
        if (roomId == null) roomId = "";
        if (playerId == null) playerId = "";
        if (payload == null) payload = NullNode.getInstance();
    }
}
