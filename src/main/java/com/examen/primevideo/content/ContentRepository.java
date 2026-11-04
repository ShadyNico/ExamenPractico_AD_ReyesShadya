package com.examen.primevideo.content;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends JpaRepository<Content, Integer> {

    @Query("""
            select c from Content c
            where (:texto is null or lower(c.title) like lower(concat('%', cast(:texto as string), '%')))
              and (:tipo is null or c.contentType = :tipo)
            """)
    Page<Content> buscar(@Param("texto") String texto, @Param("tipo") String tipo, Pageable pageable);

    @Query(value = "select nextval('content_id_seq')", nativeQuery = true)
    long siguienteId();

    boolean existsBySourceIdAndIdNot(String sourceId, Integer id);
}
