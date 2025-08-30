CREATE TABLE processed_files (
    file_name VARCHAR(512) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    etag TEXT NOT NULL
);

-- Audit logging tables
CREATE TABLE IF NOT EXISTS file_processing_audit (
    id SERIAL PRIMARY KEY,
    file_name VARCHAR(512) NOT NULL,
    total_records INT NOT NULL DEFAULT 0,
    valid_records INT NOT NULL DEFAULT 0,
    invalid_records INT NOT NULL DEFAULT 0,
    invalid_reasons TEXT,
    enriched_records INT NOT NULL DEFAULT 0,
    scored_records INT NOT NULL DEFAULT 0,
    inserted_to_review INT NOT NULL DEFAULT 0,
    processing_start_time TIMESTAMP NOT NULL,
    processing_end_time TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS processing_errors (
    id SERIAL PRIMARY KEY,
    file_name VARCHAR(512) NOT NULL,
    error TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_file_processing_audit_file_name ON file_processing_audit(file_name);
CREATE INDEX IF NOT EXISTS idx_file_processing_audit_status ON file_processing_audit(status);
CREATE INDEX IF NOT EXISTS idx_file_processing_audit_created_at ON file_processing_audit(created_at);
CREATE INDEX IF NOT EXISTS idx_processing_errors_file_name ON processing_errors(file_name);


CREATE TABLE IF NOT EXISTS suspicious_transactions (
    txn_id VARCHAR(100) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    amount_sar DECIMAL(10, 2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    timestamp_local TIMESTAMP NOT NULL,
    suspicion_score INT DEFAULT 0,
    checked BOOLEAN DEFAULT FALSE,
    checked_by VARCHAR(100),
    checked_at TIMESTAMP,
    check_notes TEXT
);

CREATE TABLE IF NOT EXISTS whitelisted_merchants (
    id SERIAL PRIMARY KEY,
    merchant_name TEXT UNIQUE NOT NULL,
    notes TEXT
);

-- Seed some default trusted merchants
INSERT INTO whitelisted_merchants (merchant_name, notes) VALUES
    ('amazon', 'Trusted partner'),
    ('apple store', 'Authorized store'),
    ('netflix', 'Recurring subscription'),
    ('spotify', 'Music subscription'),
    ('google', 'Authorized store')
ON CONFLICT (merchant_name) DO NOTHING;