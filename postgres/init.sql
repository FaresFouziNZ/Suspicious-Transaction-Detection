CREATE TABLE processed_files (
    file_name VARCHAR(512) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    etag TEXT NOT NULL
);


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