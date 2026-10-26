-- Explicit constraints also apply on MySQL, where inline REFERENCES may be ignored.
ALTER TABLE external_identities ADD CONSTRAINT uq_external_local_user UNIQUE(user_id);
ALTER TABLE external_identities ADD CONSTRAINT fk_external_user FOREIGN KEY(user_id) REFERENCES users(id);
ALTER TABLE sessions ADD CONSTRAINT fk_session_user FOREIGN KEY(user_id) REFERENCES users(id);
ALTER TABLE categories ADD CONSTRAINT fk_category_parent FOREIGN KEY(parent_id) REFERENCES categories(id);
ALTER TABLE contents ADD CONSTRAINT fk_content_category FOREIGN KEY(category_id) REFERENCES categories(id);
ALTER TABLE contents ADD CONSTRAINT fk_content_creator FOREIGN KEY(created_by) REFERENCES users(id);
ALTER TABLE content_revisions ADD CONSTRAINT fk_revision_content FOREIGN KEY(content_id) REFERENCES contents(id);
ALTER TABLE content_revisions ADD CONSTRAINT fk_revision_category FOREIGN KEY(category_id) REFERENCES categories(id);
ALTER TABLE content_revisions ADD CONSTRAINT fk_revision_creator FOREIGN KEY(created_by) REFERENCES users(id);
ALTER TABLE media_files ADD CONSTRAINT fk_media_creator FOREIGN KEY(created_by) REFERENCES users(id);
ALTER TABLE revision_media ADD CONSTRAINT fk_media_revision FOREIGN KEY(revision_id) REFERENCES content_revisions(id);
ALTER TABLE revision_media ADD CONSTRAINT fk_revision_file FOREIGN KEY(media_id) REFERENCES media_files(id);
