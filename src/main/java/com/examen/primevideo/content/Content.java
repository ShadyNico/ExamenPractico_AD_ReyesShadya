package com.examen.primevideo.content;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Entidad mapeada a la tabla content de primevideodb. */
@Entity
@Table(name = "content")
public class Content {

    // el id se toma de content_id_seq en ContentService (la tabla original no tiene autoincremento)
    @Id
    private Integer id;

    @Column(name = "source_id", nullable = false, unique = true, length = 20)
    private String sourceId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;         // MOVIE | TV_SHOW | OTHER

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "date_added")
    private LocalDate dateAdded;

    @Column(name = "release_year")
    private Short releaseYear;

    @Column(length = 30)
    private String rating;

    @Column(name = "duration_value")
    private Integer durationValue;

    @Column(name = "duration_unit", length = 20)
    private String durationUnit;        // MINUTES | SEASONS | OTHER

    @Column(name = "duration_text", length = 50)
    private String durationText;

    @Column(columnDefinition = "TEXT")
    private String description;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getDateAdded() { return dateAdded; }
    public void setDateAdded(LocalDate dateAdded) { this.dateAdded = dateAdded; }
    public Short getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Short releaseYear) { this.releaseYear = releaseYear; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public Integer getDurationValue() { return durationValue; }
    public void setDurationValue(Integer durationValue) { this.durationValue = durationValue; }
    public String getDurationUnit() { return durationUnit; }
    public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
    public String getDurationText() { return durationText; }
    public void setDurationText(String durationText) { this.durationText = durationText; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
