package com.examen.primevideo.websocket;

import com.examen.primevideo.content.ContentDtos.ContentResponse;

/**
 * Mensaje enviado a /topic/content cuando cambia la tabla content.
 * accion: CREADO | ACTUALIZADO | ELIMINADO ; cliente: id del navegador que hizo el cambio.
 */
public record ContentEvento(String accion, Integer id, ContentResponse contenido, String cliente, long timestamp) {
}
