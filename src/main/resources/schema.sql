-- La tabla content del script original tiene "id INTEGER PRIMARY KEY" sin autoincremento.
-- Para registrar nuevos contenidos se usa una secuencia que continua desde el id maximo.
CREATE SEQUENCE IF NOT EXISTS content_id_seq;
SELECT setval('content_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM content), (SELECT last_value FROM content_id_seq)));
