-- Creates a least-privilege MySQL user that authenticates via IAM token
-- instead of a static password. The EC2 instance role's rds-db:connect
-- permission generates the token; no password is ever stored.
CREATE USER IF NOT EXISTS 'compendium_app'@'%' IDENTIFIED WITH AWSAuthenticationPlugin AS 'RDS';

-- DML-only grants — the app user cannot drop tables or run DDL.
-- Flyway migrations run as the master user (SPRING_DATASOURCE_*) which
-- retains full schema privileges.
GRANT SELECT, INSERT, UPDATE, DELETE ON compendium.* TO 'compendium_app'@'%';
