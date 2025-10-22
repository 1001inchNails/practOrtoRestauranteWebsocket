package org.algos;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/websocket/{clientId}")
public class WebSocketServer {
    private static final ConcurrentHashMap<String, Session> sessions =
            new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("clientId") String clientId) {
        sessions.put(clientId, session);
        System.out.println("Client connected: " + clientId);
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("clientId") String clientId) {
        System.out.println("Received from client " + clientId + ": " + message);
        broadcast(message, clientId);
    }

    private void broadcast(String message, String senderId) {
        sessions.forEach((id, sess) -> {
            if (sess.isOpen() && !id.equals(senderId)) {
                try {
                    sess.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    System.out.println("Error sending message to client " + id);
                }
            }
        });
    }

    @OnError
    public void onError(Throwable error, Session session, @PathParam("clientId") String clientId) {
        System.out.println("Error occurred for client " + clientId);
        error.printStackTrace();
    }

    @OnClose
    public void onClose(Session session, @PathParam("clientId") String clientId) {
        sessions.remove(clientId);
        System.out.println("Client disconnected: " + clientId);
    }
}