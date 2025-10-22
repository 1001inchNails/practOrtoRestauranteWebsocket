package org.algos;

import org.glassfish.tyrus.server.Server;

public class WebSocketServerApp {
    public static void main(String[] args) {
        Server server = new Server("localhost", 8025, null, null, WebSocketServer.class);

        try {
            server.start();
            System.out.println("WebSocket server started on port 8025");
            Thread.currentThread().join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            server.stop();
        }
    }
}