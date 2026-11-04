package com.examen.primevideo.content;

import com.examen.primevideo.content.ContentDtos.ContentRequest;
import com.examen.primevideo.content.ContentDtos.ContentResponse;
import com.examen.primevideo.content.ContentDtos.Pagina;
import com.examen.primevideo.websocket.ContentEvento;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional
public class ContentService {

    private final ContentRepository repo;
    private final EntityManager em;
    private final ApplicationEventPublisher eventos;

    public ContentService(ContentRepository repo, EntityManager em, ApplicationEventPublisher eventos) {
        this.repo = repo;
        this.em = em;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public Pagina<ContentResponse> listar(String buscar, String tipo, int pagina, int tamanio) {
        Page<Content> p = repo.buscar(vacioANull(buscar), vacioANull(tipo),
                PageRequest.of(pagina, tamanio, Sort.by(Sort.Direction.DESC, "id")));
        return new Pagina<>(p.getContent().stream().map(ContentResponse::de).toList(),
                p.getNumber(), p.getSize(), p.getTotalPages(), p.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ContentResponse obtener(Integer id) {
        return ContentResponse.de(buscar(id));
    }

    public ContentResponse crear(ContentRequest req, String cliente) {
        Content c = new Content();
        c.setId((int) repo.siguienteId());
        copiar(req, c);
        if (c.getSourceId() == null) {
            c.setSourceId("s" + c.getId());
        }
        validarSourceId(c);
        em.persist(c);
        ContentResponse res = ContentResponse.de(c);
        publicar("CREADO", res.id(), res, cliente);
        return res;
    }

    public ContentResponse actualizar(Integer id, ContentRequest req, String cliente) {
        Content c = buscar(id);
        copiar(req, c);
        if (c.getSourceId() == null) {
            c.setSourceId("s" + id);
        }
        validarSourceId(c);
        ContentResponse res = ContentResponse.de(c);
        publicar("ACTUALIZADO", id, res, cliente);
        return res;
    }

    public void eliminar(Integer id, String cliente) {
        // content_person, content_genre y content_country se eliminan por ON DELETE CASCADE
        repo.delete(buscar(id));
        publicar("ELIMINADO", id, null, cliente);
    }

    /** El evento se envia por WebSocket cuando la transaccion hace COMMIT (NotificadorWebSocket). */
    private void publicar(String accion, Integer id, ContentResponse contenido, String cliente) {
        eventos.publishEvent(new ContentEvento(accion, id, contenido, cliente, System.currentTimeMillis()));
    }

    private Content buscar(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El contenido " + id + " no existe"));
    }

    private void validarSourceId(Content c) {
        if (repo.existsBySourceIdAndIdNot(c.getSourceId(), c.getId())) {
            throw new ResponseStatusException(CONFLICT, "El source_id '" + c.getSourceId() + "' ya existe");
        }
    }

    private static void copiar(ContentRequest r, Content c) {
        c.setSourceId(vacioANull(r.sourceId()));
        c.setContentType(r.contentType());
        c.setTitle(r.title().trim());
        c.setDateAdded(r.dateAdded());
        c.setReleaseYear(r.releaseYear());
        c.setRating(vacioANull(r.rating()));
        c.setDurationValue(r.durationValue());
        c.setDurationUnit(r.durationValue() == null ? null : r.durationUnit());
        c.setDurationText(textoDuracion(r.durationValue(), r.durationUnit()));
        c.setDescription(vacioANull(r.description()));
    }

    /** Mismo formato que el dataset: "113 min" o "2 Seasons". */
    private static String textoDuracion(Integer valor, String unidad) {
        if (valor == null) return null;
        if ("MINUTES".equals(unidad)) return valor + " min";
        if ("SEASONS".equals(unidad)) return valor + (valor == 1 ? " Season" : " Seasons");
        return String.valueOf(valor);
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
