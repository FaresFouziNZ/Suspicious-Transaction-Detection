CREATE TABLE IF NOT EXISTS transactions (
    txn_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    amount_sar DECIMAL(10, 2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    timestamp_local TIMESTAMP NOT NULL,
    suspicion_score INT DEFAULT 0,
    checked BOOLEAN DEFAULT FALSE,
    checked_by VARCHAR(100),
    checked_at TIMESTAMP,
    check_notes TEXT
);