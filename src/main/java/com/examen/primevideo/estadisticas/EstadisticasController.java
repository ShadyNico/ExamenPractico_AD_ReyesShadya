package com.examen.primevideo.estadisticas;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * POST /api/estadisticas?hilos=N   -> 202 inmediato; el calculo corre en hilos y llega por WebSocket
 * GET  /api/estadisticas/sin-hilos -> version bloqueante: responde cuando termina todo el calculo
 * GET  /api/estadisticas/ultimo    -> ultimo resultado calculado
 */
@RestController
@RequestMapping("/api/estadisticas")
public class EstadisticasController {

    private final EstadisticasService service;

    public EstadisticasController(EstadisticasService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> iniciar(@RequestParam(defaultValue = "8") int hilos) {
        return service.iniciar(hilos);
    }

    @GetMapping("/sin-hilos")
    public Resultado sinHilos() {
        return service.procesarSinHilos();
    }

    @GetMapping("/ultimo")
    public ResponseEntity<Resultado> ultimo() {
        Resultado r = service.ultimo();
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }
}
