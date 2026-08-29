ALTER TABLE ai_configuration ADD COLUMN inference_engine VARCHAR(16) NOT NULL DEFAULT 'DOUZERO';
ALTER TABLE ai_configuration ADD COLUMN api_base_url VARCHAR(1024) NOT NULL DEFAULT '';
ALTER TABLE ai_configuration ADD COLUMN api_model VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE ai_configuration ADD COLUMN encrypted_api_key LONGBLOB NULL;
ALTER TABLE ai_configuration ADD COLUMN api_key_iv BINARY(12) NULL;
