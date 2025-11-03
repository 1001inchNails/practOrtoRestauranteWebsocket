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
                session.getBasicRemote().sendText(crearJsonError("CLIENT_ID_EXISTS",
                        "ID ya existe: " + clientId));
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY,
                        "ID duplicada"));
                return;
            } catch (IOException e) {
                System.out.println("Error: rechazo de conexion duplicada");
            }
        }

        sessions.put(clientId, session);
        System.out.println("Conexion: " + clientId);

        notificarConexionCliente(clientId);


    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("clientId") String clientId) {
        System.out.println("Recibido de " + clientId + ": " + message);

        try {
            // parsear el mensaje JSON
            MensajeJson msg = parsearMensaje(message);

            // validar el destino
            if (msg.getDestino() != null && !msg.getDestino().isEmpty()) {
                if (!sessions.containsKey(msg.getDestino())) {
                    String errorMsg = crearJsonError("DESTINATION_NOT_FOUND",
                            "Cliente destino no encontrado: " + msg.getDestino());
                    session.getBasicRemote().sendText(errorMsg);
                    return;
                }

                // enviar mensaje al destino específico
                sendToDestino(msg, clientId);
            } else {
                // broadcast a todos los clientes
                broadcast(message, clientId);
            }

        } catch (Exception e) {
            try {
                String errorMsg = crearJsonError("INVALID_MESSAGE",
                        "Formato de mensaje erroneo ");
                session.getBasicRemote().sendText(errorMsg);
            } catch (IOException ioException) {
                System.out.println("Error al enviar");
            }
        }
    }

    private void sendToDestino(MensajeJson msg, String senderId) {
        Session destinationSession = sessions.get(msg.getDestino());
        if (destinationSession != null && destinationSession.isOpen()) {
            try {
                String formattedMessage = crearMensajeJson(
                        senderId,
                        msg.getMessage(),
                        msg.getType(),
                        msg.getDestino(),
                        msg.getTimestamp()
                );

                destinationSession.getBasicRemote().sendText(formattedMessage);
                System.out.println("Enviado de " + senderId + " a " + msg.getDestino());

            } catch (IOException e) {
                System.out.println("Error al enviar mensaje a cliente " + msg.getDestino());
                notificarSenderError(senderId, msg.getDestino());
            }
        } else {
            // si el destino se desconecta despues de la validacion inicial
            notificarSenderError(senderId, msg.getDestino());
        }
    }

    private void notificarSenderError(String senderId, String destinationId) {
        Session senderSession = sessions.get(senderId);
        if (senderSession != null && senderSession.isOpen()) {
            try {
                String errorMsg = crearJsonError("DESTINATION_DISCONNECTED",
                        "Cliente destino desconectado: " + destinationId);
                senderSession.getBasicRemote().sendText(errorMsg);
            } catch (IOException e) {
                System.out.println("Error al notificar error a remitente");
            }
        }
    }

    private void broadcast(String message, String senderId) {
        sessions.forEach((id, sess) -> {
            if (sess.isOpen() && !id.equals(senderId)) {
                try {
                    sess.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    System.out.println("Error al enviar mensaje a cliente " + id);
                }
            }
        });
    }

    @OnError
    public void onError(Throwable error, Session session, @PathParam("clientId") String clientId) {
        System.out.println("Error para cliente " + clientId);
        error.printStackTrace();
    }

    @OnClose
    public void onClose(Session session, @PathParam("clientId") String clientId) {
        sessions.remove(clientId);
        System.out.println("Desconexion de : " + clientId);

        notificarDesconexionCliente(clientId);
    }

    private void notificarConexionCliente(String clientId) {
        MensajeJson connectMessage = crearMensajeSistema(
                clientId,
                "Cliente conectado",
                "client_connect"
        );

        broadcast(crearJsonMensajeSistema(connectMessage), "SYSTEM");
    }

    private void notificarDesconexionCliente(String clientId) {
        MensajeJson disconnectMessage = crearMensajeSistema(
                clientId,
                "Cliente desconectado\n----------------------------------------------------------\n",
                "client_disconnect"
        );

        broadcast(crearJsonMensajeSistema(disconnectMessage), "SYSTEM");
    }

    private String crearJsonError(String errorCode, String errorMessage) {
        return String.format(
                "{\"type\":\"error\",\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                errorCode, errorMessage, System.currentTimeMillis()
        );
    }

    private String crearJsonSuccess(String status, String message) {
        return String.format(
                "{\"type\":\"success\",\"status\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                status, message, System.currentTimeMillis()
        );
    }

    private String crearMensajeJson(String sender, String message, String type,
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

    private String crearJsonMensajeSistema(MensajeJson msg) {
        return String.format(
                "{\"type\":\"%s\",\"sender\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                msg.getType(),
                msg.getSender(),
                msg.getMessage(),
                msg.getTimestamp()
        );
    }

    private MensajeJson crearMensajeSistema(String sender, String message, String type) {
        MensajeJson systemMsg = new MensajeJson();
        systemMsg.setType(type != null ? type : "system");
        systemMsg.setSender(sender != null ? sender : "SYSTEM");
        systemMsg.setMessage(message != null ? message : "");
        systemMsg.setTimestamp(System.currentTimeMillis());
        return systemMsg;
    }

    private static class MensajeJson {
        private String type;
        private String message;
        private String sender;
        private String destino;
        private long timestamp;

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

    private MensajeJson parsearMensaje(String jsonString) {
        MensajeJson msg = new MensajeJson();

        if (jsonString.contains("\"type\"")) {
            msg.type = extraerValor(jsonString, "type");
        }
        if (jsonString.contains("\"message\"")) {
            msg.message = extraerValor(jsonString, "message");
        }
        if (jsonString.contains("\"sender\"")) {
            msg.sender = extraerValor(jsonString, "sender");
        }
        if (jsonString.contains("\"destino\"")) {
            msg.destino = extraerValor(jsonString, "destino");
        }
        if (jsonString.contains("\"timestamp\"")) {
            try {
                msg.timestamp = Long.parseLong(extraerValor(jsonString, "timestamp"));
            } catch (NumberFormatException e) {
                msg.timestamp = System.currentTimeMillis();
            }
        }

        return msg;
    }

    private String extraerValor(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":\"";
            int start = json.indexOf(searchKey) + searchKey.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    // para debugging
    public static void printConnectedClients() {
        System.out.println("Clientes conectados: " + sessions.keySet());
    }
}