package org.algos;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/websocket/{clientId}")
public class WebSocketServer {
    private static final ConcurrentHashMap<String, Session> sessions =
            new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("clientId") String clientId) {
        // verificar si el clientId ya existe
        if (sessions.containsKey(clientId)) {
            try {
                session.getBasicRemote().sendText(createErrorJson("CLIENT_ID_EXISTS",
                        "Client ID already exists: " + clientId));
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY,
                        "Duplicate client ID"));
                return;
            } catch (IOException e) {
                System.out.println("Error rejecting duplicate connection");
            }
        }

        sessions.put(clientId, session);
        System.out.println("Client connected: " + clientId);

        // Notificar a TODOS los clientes sobre la nueva conexión
        notifyClientConnection(clientId);


    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("clientId") String clientId) {
        System.out.println("Received from client " + clientId + ": " + message);

        try {
            // Parsear el mensaje JSON
            JsonMessage msg = parseMessage(message);

            // Validar el destino
            if (msg.getDestino() != null && !msg.getDestino().isEmpty()) {
                if (!sessions.containsKey(msg.getDestino())) {
                    // Destino no encontrado, enviar error al remitente
                    String errorMsg = createErrorJson("DESTINATION_NOT_FOUND",
                            "Destination client not found: " + msg.getDestino());
                    session.getBasicRemote().sendText(errorMsg);
                    return;
                }

                // Enviar mensaje al destino específico
                sendToDestination(msg, clientId);
            } else {
                // Broadcast a todos los clientes excepto el remitente
                broadcast(message, clientId);
            }

        } catch (Exception e) {
            // Error parsing JSON, enviar mensaje de error
            try {
                String errorMsg = createErrorJson("INVALID_MESSAGE",
                        "Invalid message format");
                session.getBasicRemote().sendText(errorMsg);
            } catch (IOException ioException) {
                System.out.println("Error sending error message");
            }
        }
    }

    private void sendToDestination(JsonMessage msg, String senderId) {
        Session destinationSession = sessions.get(msg.getDestino());
        if (destinationSession != null && destinationSession.isOpen()) {
            try {
                // Crear mensaje formateado con información del remitente
                String formattedMessage = createMessageJson(
                        senderId,
                        msg.getMessage(),
                        msg.getType(),
                        msg.getDestino(),
                        msg.getTimestamp()
                );

                destinationSession.getBasicRemote().sendText(formattedMessage);
                System.out.println("Message sent from " + senderId + " to " + msg.getDestino());

            } catch (IOException e) {
                System.out.println("Error sending message to client " + msg.getDestino());
                // Notificar al remitente sobre el error de envío
                notifySenderError(senderId, msg.getDestino());
            }
        } else {
            // El destino se desconectó después de la validación inicial
            notifySenderError(senderId, msg.getDestino());
        }
    }

    private void notifySenderError(String senderId, String destinationId) {
        Session senderSession = sessions.get(senderId);
        if (senderSession != null && senderSession.isOpen()) {
            try {
                String errorMsg = createErrorJson("DESTINATION_DISCONNECTED",
                        "Destination client disconnected: " + destinationId);
                senderSession.getBasicRemote().sendText(errorMsg);
            } catch (IOException e) {
                System.out.println("Error notifying sender about disconnected destination");
            }
        }
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

        // Notificar a otros clientes sobre la desconexión
        notifyClientDisconnection(clientId);
    }

    private void notifyClientConnection(String clientId) {
        JsonMessage connectMessage = createSystemMessage(
                clientId,
                "Client connected",
                "client_connect"
        );

        broadcast(createSystemMessageJson(connectMessage), "SYSTEM");
    }

    private void notifyClientDisconnection(String clientId) {
        JsonMessage disconnectMessage = createSystemMessage(
                clientId,
                "Client disconnected\n----------------------------------------------------------\n",
                "client_disconnect"
        );

        broadcast(createSystemMessageJson(disconnectMessage), "SYSTEM");
    }

    // Métodos auxiliares para crear JSON (sin cambios)
    private String createErrorJson(String errorCode, String errorMessage) {
        return String.format(
                "{\"type\":\"error\",\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                errorCode, errorMessage, System.currentTimeMillis()
        );
    }

    private String createSuccessJson(String status, String message) {
        return String.format(
                "{\"type\":\"success\",\"status\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                status, message, System.currentTimeMillis()
        );
    }

    private String createMessageJson(String sender, String message, String type,
                                     String destination, long timestamp) {
        return String.format(
                "{\"type\":\"%s\",\"sender\":\"%s\",\"destino\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                type != null ? type : "chat",
                sender,
                destination,
                message,
                timestamp
        );
    }

    private String createSystemMessageJson(JsonMessage msg) {
        return String.format(
                "{\"type\":\"%s\",\"sender\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                msg.getType(),
                msg.getSender(),
                msg.getMessage(),
                msg.getTimestamp()
        );
    }

    private JsonMessage createSystemMessage(String sender, String message, String type) {
        JsonMessage systemMsg = new JsonMessage();
        systemMsg.setType(type != null ? type : "system");
        systemMsg.setSender(sender != null ? sender : "SYSTEM");
        systemMsg.setMessage(message != null ? message : "");
        systemMsg.setTimestamp(System.currentTimeMillis());
        return systemMsg;
    }

    // Clase interna para parsear mensajes JSON (sin cambios)
    private static class JsonMessage {
        private String type;
        private String message;
        private String sender;
        private String destino;
        private long timestamp;

        // Getters y setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getSender() { return sender; }
        public void setSender(String sender) { this.sender = sender; }

        public String getDestino() { return destino; }
        public void setDestino(String destino) { this.destino = destino; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    private JsonMessage parseMessage(String jsonString) {
        // Parseo simple - en producción usarías una librería JSON como Jackson o Gson
        JsonMessage msg = new JsonMessage();

        // Extraer valores básicos del JSON
        if (jsonString.contains("\"type\"")) {
            msg.type = extractValue(jsonString, "type");
        }
        if (jsonString.contains("\"message\"")) {
            msg.message = extractValue(jsonString, "message");
        }
        if (jsonString.contains("\"sender\"")) {
            msg.sender = extractValue(jsonString, "sender");
        }
        if (jsonString.contains("\"destino\"")) {
            msg.destino = extractValue(jsonString, "destino");
        }
        if (jsonString.contains("\"timestamp\"")) {
            try {
                msg.timestamp = Long.parseLong(extractValue(jsonString, "timestamp"));
            } catch (NumberFormatException e) {
                msg.timestamp = System.currentTimeMillis();
            }
        }

        return msg;
    }

    private String extractValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":\"";
            int start = json.indexOf(searchKey) + searchKey.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    // Método para obtener clientes conectados (útil para debugging)
    public static void printConnectedClients() {
        System.out.println("Connected clients: " + sessions.keySet());
    }
}