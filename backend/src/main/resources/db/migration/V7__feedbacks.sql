CREATE TABLE feedbacks (
 id VARCHAR(36) PRIMARY KEY,
 subject VARCHAR(100) NOT NULL,
 description VARCHAR(200) NOT NULL,
 created_at BIGINT NOT NULL
);
CREATE INDEX idx_feedbacks_created_at ON feedbacks(created_at DESC);
