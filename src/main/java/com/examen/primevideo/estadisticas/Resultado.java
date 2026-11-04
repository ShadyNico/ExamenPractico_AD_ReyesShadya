package com.examen.primevideo.estadisticas;

import java.util.List;
import java.util.Map;

/** Resultado final del procesamiento (se envia por /topic/estadisticas y por REST). */
public record Resultado(
        String jobId,
        String modo,                    // HILOS | SIN_HILOS
        int hilos,
        int totalContenidos,
        Map<String, Integer> porTipo,
        List<Conteo> porGenero,         // top 15
        List<PorAnio> porAnio,          // ultimos 20 anios
        List<Conteo> porRating,         // top 10
        double duracionPromedioPeliculasMin,
        double temporadasPromedioSeries,
        List<Similar> similares,        // top 10 pares mas parecidos
        List<DetalleHilo> detalleHilos,
        long comparaciones,
        long msCarga,
        long msProcesamiento,
        long msTotal,
        String hiloCoordinador,
        long inicio,
        long fin) {

    public record Conteo(String nombre, int cantidad) {
    }

    public record PorAnio(int anio, int peliculas, int series) {
    }

    public record Similar(int idA, String tituloA, int idB, String tituloB, double similitud, int elementosComunes) {
    }

    public record DetalleHilo(String hilo, int procesados, long comparaciones, long ms) {
    }
}
