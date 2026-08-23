-- ShedLock stores its distributed-cron locks in a plain table it manages itself —
-- Hibernate's ddl-auto never creates it because it isn't a JPA entity. Spring Boot runs
-- this file on every startup (idempotent via IF NOT EXISTS) before the scheduled jobs
-- (booking hold/offer cleanup, blacklist cleanup) can acquire their first lock.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- Powers fuzzy/typo-tolerant event search (EventRepository#searchEvents uses similarity()).
-- Most managed Postgres providers (Render, Supabase, RDS, etc.) allow this extension without
-- superuser; if a host ever rejects it, similarity() calls in searchEvents will fail — swap
-- back to a plain ILIKE '%...%' query in that case.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_event_title_trgm ON event USING gin (title gin_trgm_ops);
