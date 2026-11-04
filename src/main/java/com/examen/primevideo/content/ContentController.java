package com.examen.primevideo.content;

import com.examen.primevideo.content.ContentDtos.ContentRequest;
import com.examen.primevideo.content.ContentDtos.ContentResponse;
import com.examen.primevideo.content.ContentDtos.Pagina;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** CRUD REST de la tabla content. */
@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final ContentService service;

    public ContentController(ContentService service) {
        this.service = service;
    }

    @GetMapping
    public Pagina<ContentResponse> listar(@RequestParam(required = false) String buscar,
                                          @RequestParam(required = false) String tipo,
                                          @RequestParam(defaultValue = "0") int pagina,
                                          @RequestParam(defaultValue = "10") int tamanio) {
        return service.listar(buscar, tipo, Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), 100));
    }

    @GetMapping("/{id}")
    public ContentResponse obtener(@PathVariable Integer id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContentResponse crear(@Valid @RequestBody ContentRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    public ContentResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ContentRequest req) {
        return service.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        service.eliminar(id);
    }
}
