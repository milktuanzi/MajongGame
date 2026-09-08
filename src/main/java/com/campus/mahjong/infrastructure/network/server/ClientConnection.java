package com.campus.mahjong.infrastructure.network.server;

import com.campus.mahjong.infrastructure.network.protocol.JsonMessageCodec;
import com.campus.mahjong.infrastructure.network.protocol.MessageEnvelope;
import com.campus.mahjong.model.common.MahjongTypes.PlayerId;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 一个远端玩家的连接；同一 Socket 的写入在此处串行化。 */
final class ClientConnection implements AutoCloseable {
    private final Socket socket;
    private final RoomManager rooms;
    private final Consumer<ClientConnection> onClosed;
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final DataInputStream input;
    private final DataOutputStream output;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile PlayerId playerId;
    private volatile String roomId = "";

    ClientConnection(Socket socket, RoomManager rooms, Consumer<ClientConnection> onClosed) throws IOException {
        this.socket = socket;
        this.rooms = rooms;
        this.onClosed = onClosed;
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    void start() { Thread.ofVirtual().name("mahjong-client-" + socket.getRemoteSocketAddress()).start(this::readLoop); }

    PlayerId playerId() { return playerId; }
    String roomId() { return roomId; }

    void bind(PlayerId playerId, String roomId) {
        this.playerId = playerId;
        this.roomId = roomId;
    }

    synchronized void send(MessageEnvelope envelope) {
        if (closed.get()) return;
        try {
            codec.write(output, envelope);
        } catch (IOException exception) {
            close();
        }
    }

    private void readLoop() {
        try {
            while (!closed.get()) {
                MessageEnvelope envelope = codec.read(input);
                rooms.handle(this, envelope);
            }
        } catch (IOException | RuntimeException ignored) {
            close();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) {}
        rooms.disconnected(this);
        onClosed.accept(this);
    }
}
