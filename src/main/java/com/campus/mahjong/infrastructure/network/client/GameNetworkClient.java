package com.campus.mahjong.infrastructure.network.client;

import com.campus.mahjong.infrastructure.network.protocol.JsonMessageCodec;
import com.campus.mahjong.infrastructure.network.protocol.MessageEnvelope;
import com.campus.mahjong.infrastructure.network.protocol.MessageType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 单个 JavaFX 进程复用的一条 TCP 长连接。 */
public final class GameNetworkClient implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final Map<String, CompletableFuture<MessageEnvelope>> pending = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<MessageEnvelope>> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mahjong-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean open = true;

    private GameNetworkClient(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5_000);
        socket.setTcpNoDelay(true);
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        Thread.ofVirtual().name("mahjong-network-reader").start(this::readLoop);
        heartbeat.scheduleAtFixedRate(this::sendPing, 5, 5, TimeUnit.SECONDS);
    }

    public static GameNetworkClient connect(String host, int port) throws IOException {
        return new GameNetworkClient(host, port);
    }

    public CompletableFuture<MessageEnvelope> request(MessageType type, String roomId, String playerId,
                                                      long expectedRevision, Object payload) {
        if (!open) return CompletableFuture.failedFuture(new IllegalStateException("网络连接已关闭"));
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<MessageEnvelope> future = new CompletableFuture<>();
        pending.put(requestId, future);
        MessageEnvelope envelope = new MessageEnvelope(MessageEnvelope.CURRENT_VERSION, requestId,
                type, roomId, playerId, expectedRevision, JsonMessageCodec.tree(payload));
        try {
            send(envelope);
        } catch (RuntimeException exception) {
            pending.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future.orTimeout(8, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> pending.remove(requestId, future));
    }

    public AutoCloseable observe(Consumer<MessageEnvelope> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private synchronized void send(MessageEnvelope envelope) {
        try {
            codec.write(output, envelope);
        } catch (IOException exception) {
            close();
            throw new IllegalStateException("发送消息失败", exception);
        }
    }

    private void readLoop() {
        try {
            while (open) {
                MessageEnvelope envelope = codec.read(input);
                if (envelope.messageType() == MessageType.RESPONSE && !envelope.requestId().isBlank()) {
                    CompletableFuture<MessageEnvelope> future = pending.remove(envelope.requestId());
                    if (future != null) future.complete(envelope);
                    continue;
                }
                if (envelope.messageType() != MessageType.PONG) {
                    listeners.forEach(listener -> {
                        try { listener.accept(envelope); } catch (RuntimeException ignored) {}
                    });
                }
            }
        } catch (IOException | RuntimeException exception) {
            close();
        }
    }

    private void sendPing() {
        if (!open) return;
        try {
            send(new MessageEnvelope(MessageEnvelope.CURRENT_VERSION, "", MessageType.PING,
                    "", "", -1, JsonMessageCodec.tree("ping")));
        } catch (RuntimeException ignored) {}
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        heartbeat.shutdownNow();
        try { socket.close(); } catch (IOException ignored) {}
        IllegalStateException failure = new IllegalStateException("网络连接已关闭");
        pending.values().forEach(future -> future.completeExceptionally(failure));
        pending.clear();
    }
}
