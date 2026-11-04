package com.examen.primevideo.websocket;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Valor inicial de clientes conectados (los cambios posteriores llegan por /topic/clientes). */
@RestController
public class ClientesController {

    private final NotificadorWebSocket notificador;

    public ClientesController(NotificadorWebSocket notificador) {
        this.notificador = notificador;
    }

    @GetMapping("/api/clientes")
    public Map<String, Integer> clientes() {
        return Map.of("conectados", notificador.conectados());
    }
}
