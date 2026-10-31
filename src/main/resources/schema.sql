CREATE DATABASE IF NOT EXISTS data_guard CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE data_guard;

CREATE TABLE IF NOT EXISTS app_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_app_users_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS data_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    external_id VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NULL,
    category VARCHAR(60) NOT NULL,
    details VARCHAR(500) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_data_records_external_id UNIQUE (external_id),
    CONSTRAINT uk_data_records_fingerprint UNIQUE (fingerprint),
    INDEX idx_data_records_category (category),
    INDEX idx_data_records_created_at (created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS duplicate_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    external_id VARCHAR(80) NOT NULL,
    submitted_by VARCHAR(50) NOT NULL,
    matched_record_id BIGINT NULL,
    reason VARCHAR(40) NOT NULL,
    attempted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_duplicate_attempts_attempted_at (attempted_at),
    INDEX idx_duplicate_attempts_external_id (external_id),
    CONSTRAINT fk_duplicate_attempts_matched_record FOREIGN KEY (matched_record_id) REFERENCES data_records(id) ON DELETE SET NULL
) ENGINE=InnoDB;
