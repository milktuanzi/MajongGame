package com.campus.mahjong.infrastructure.network.server;

import com.campus.mahjong.infrastructure.game.InMemoryGameSessionService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 房主进程内的临时 TCP 服务端。 */
public final class GameServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final RoomManager rooms;
    private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();
    private final Thread acceptThread;
    private final java.util.concurrent.ScheduledExecutorService timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "mahjong-round-timer"); thread.setDaemon(true); return thread;
    });
    private volatile boolean running = true;

    private GameServer(int port, com.campus.mahjong.model.ai.TeacherExplanationProvider provider) throws IOException {
        rooms = new RoomManager(new InMemoryGameSessionService(), provider);
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        timer.scheduleAtFixedRate(rooms::tick, 100, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        acceptThread = Thread.ofVirtual().name("mahjong-server-accept").start(this::acceptLoop);
    }

    public static GameServer open(int port) throws IOException { return new GameServer(port, new com.campus.mahjong.model.ai.MockTeacherExplanationProvider()); }

    public static GameServer open(int port, com.campus.mahjong.model.ai.TeacherExplanationProvider provider) throws IOException {
        return new GameServer(port, provider);
    }

    public int port() { return serverSocket.getLocalPort(); }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ClientConnection connection = new ClientConnection(socket, rooms,
                        closed -> connections.remove(closed));
                connections.add(connection);
                connection.start();
            } catch (IOException exception) {
                if (running) System.err.println("接受热点客户端连接失败: " + exception.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running = false;
        timer.shutdownNow();
        try { serverSocket.close(); } catch (IOException ignored) {}
        for (ClientConnection connection : connections.toArray(ClientConnection[]::new)) connection.close();
        acceptThread.interrupt();
    }
}
