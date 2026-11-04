package com.examen.primevideo.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Publica los cambios del CRUD y el numero de clientes conectados. */
@Component
public class NotificadorWebSocket {

    private final SimpMessagingTemplate ws;
    private final AtomicInteger clientes = new AtomicInteger();

    public NotificadorWebSocket(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    /** Se ejecuta solo despues del COMMIT: nunca se notifica un cambio que termino en rollback. */
    @TransactionalEventListener
    public void alConfirmar(ContentEvento evento) {
        ws.convertAndSend("/topic/content", evento);
    }

    @EventListener(SessionConnectedEvent.class)
    public void conectado() {
        publicarClientes(clientes.incrementAndGet());
    }

    @EventListener(SessionDisconnectEvent.class)
    public void desconectado() {
        publicarClientes(clientes.updateAndGet(n -> Math.max(0, n - 1)));
    }

    private void publicarClientes(int n) {
        ws.convertAndSend("/topic/clientes", (Object) Map.of("conectados", n));
    }

    public int conectados() {
        return clientes.get();
    }
}
