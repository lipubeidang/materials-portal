CREATE TABLE users (
 id VARCHAR(36) PRIMARY KEY, username VARCHAR(64) NOT NULL UNIQUE,
 display_name VARCHAR(100) NOT NULL, password_hash VARCHAR(100), role VARCHAR(20) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, created_at BIGINT NOT NULL
);
CREATE TABLE external_identities (
 external_id VARCHAR(160) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES users(id)
);
CREATE TABLE sessions (
 token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES users(id),
 csrf VARCHAR(64) NOT NULL, source VARCHAR(16) NOT NULL, expires_at BIGINT NOT NULL,
 access_token TEXT, refresh_token TEXT, token_expires BIGINT, external_id VARCHAR(160), checked_at BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE auth_transactions (
 id VARCHAR(64) PRIMARY KEY, browser_hash VARCHAR(64) NOT NULL, verifier VARCHAR(128) NOT NULL,
 expires_at BIGINT NOT NULL, bind_user VARCHAR(36), redirect_uri VARCHAR(1000) NOT NULL
);
CREATE TABLE categories (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(100) NOT NULL, slug VARCHAR(80) NOT NULL UNIQUE,
 parent_id VARCHAR(36), type VARCHAR(20) NOT NULL, template VARCHAR(20) NOT NULL,
 sort_order INT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE, navigation BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE contents (
 id VARCHAR(36) PRIMARY KEY, category_id VARCHAR(36) NOT NULL REFERENCES categories(id),
 draft_revision_id VARCHAR(36), published_revision_id VARCHAR(36), version INT NOT NULL,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, created_by VARCHAR(36) NOT NULL REFERENCES users(id),
 created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, published_at BIGINT
);
CREATE TABLE content_revisions (
 id VARCHAR(36) PRIMARY KEY, content_id VARCHAR(36) NOT NULL REFERENCES contents(id),
 category_id VARCHAR(36) NOT NULL REFERENCES categories(id), payload TEXT NOT NULL,
 created_by VARCHAR(36) NOT NULL REFERENCES users(id), created_at BIGINT NOT NULL
);
CREATE TABLE media_files (
 id VARCHAR(36) PRIMARY KEY, original_name VARCHAR(255) NOT NULL, mime VARCHAR(100) NOT NULL,
 size_bytes BIGINT NOT NULL, created_by VARCHAR(36) NOT NULL REFERENCES users(id), created_at BIGINT NOT NULL
);
CREATE TABLE revision_media (
 revision_id VARCHAR(36) NOT NULL REFERENCES content_revisions(id), media_id VARCHAR(36) NOT NULL REFERENCES media_files(id),
 PRIMARY KEY(revision_id, media_id)
);
CREATE TABLE site_settings (id INT PRIMARY KEY, payload TEXT NOT NULL, version INT NOT NULL);
CREATE TABLE audit_logs (
 id VARCHAR(36) PRIMARY KEY, actor VARCHAR(36), action VARCHAR(80) NOT NULL,
 object_id VARCHAR(160), created_at BIGINT NOT NULL
);
CREATE INDEX idx_content_published ON contents(published_revision_id, deleted);
CREATE INDEX idx_sessions_user ON sessions(user_id);
