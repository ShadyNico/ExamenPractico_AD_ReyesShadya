package com.examen.primevideo.content;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/** Records de entrada/salida del CRUD de content. */
//un dto permite transportar datos de manera segura entre el clientee y servidor o entre sus capas.
public final class ContentDtos {

    private ContentDtos() {
    }

    public record ContentRequest(
            @Size(max = 20) String sourceId,
            @NotBlank @Pattern(regexp = "MOVIE|TV_SHOW|OTHER") String contentType,
            @NotBlank @Size(max = 500) String title,
            LocalDate dateAdded,
            @Min(1900) @Max(2100) Short releaseYear,
            @Size(max = 30) String rating,
            @PositiveOrZero Integer durationValue,
            @Pattern(regexp = "MINUTES|SEASONS|OTHER") String durationUnit,
            @Size(max = 2000) String description) {
    }

    public record ContentResponse(Integer id, String sourceId, String contentType, String title, LocalDate dateAdded,
                                  Short releaseYear, String rating, Integer durationValue, String durationUnit,
                                  String durationText, String description) {
        public static ContentResponse de(Content c) {
            return new ContentResponse(c.getId(), c.getSourceId(), c.getContentType(), c.getTitle(), c.getDateAdded(),
                    c.getReleaseYear(), c.getRating(), c.getDurationValue(), c.getDurationUnit(),
                    c.getDurationText(), c.getDescription());
        }
    }

    public record Pagina<T>(List<T> contenido, int pagina, int tamanio, int totalPaginas, long totalElementos) {
    }
}
