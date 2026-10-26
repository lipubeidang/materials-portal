CREATE TABLE site_statistics (
 id INT PRIMARY KEY,
 total_visits BIGINT NOT NULL DEFAULT 0
);

INSERT INTO site_statistics (id, total_visits) VALUES (1, 0);
