ALTER TABLE link_access_events
    DROP COLUMN IF EXISTS country_code,
    DROP COLUMN IF EXISTS region,
    DROP COLUMN IF EXISTS city;
