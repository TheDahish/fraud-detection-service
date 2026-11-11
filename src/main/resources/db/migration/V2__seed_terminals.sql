-- Demo reference data. In production, terminals are managed by the onboarding service.
INSERT INTO terminal (terminal_id, status, created_at) VALUES
    ('T-1001', 'ACTIVE',  CURRENT_TIMESTAMP),
    ('T-1002', 'ACTIVE',  CURRENT_TIMESTAMP),
    ('T-9999', 'BLOCKED', CURRENT_TIMESTAMP);
