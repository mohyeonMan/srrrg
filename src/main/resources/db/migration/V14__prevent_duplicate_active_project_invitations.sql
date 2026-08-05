WITH duplicate_invitations AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY project_id, LOWER(email) ORDER BY created_at DESC, id DESC) AS row_number
    FROM project_invitations
    WHERE cancelled_at IS NULL AND accepted_at IS NULL
)
UPDATE project_invitations
SET cancelled_at = CURRENT_TIMESTAMP
WHERE id IN (SELECT id FROM duplicate_invitations WHERE row_number > 1);

CREATE UNIQUE INDEX uq_project_invitations_active_email
    ON project_invitations (project_id, LOWER(email))
    WHERE cancelled_at IS NULL AND accepted_at IS NULL;
