-- links, campaigns, projects의 삭제 표시를 deleted_at 타임스탬프로 통일한다.
-- projects의 archived_at은 "보관"이라는 없는 개념을 이름에 새기고 있었다.

ALTER TABLE links ADD COLUMN deleted_at TIMESTAMPTZ;
UPDATE links SET deleted_at = updated_at WHERE is_deleted;
-- 의존하는 부분 인덱스 ix_links_active_campaign_id, ix_links_active_project_id가 함께 사라진다.
ALTER TABLE links DROP COLUMN is_deleted;

ALTER TABLE campaigns ADD COLUMN deleted_at TIMESTAMPTZ;
UPDATE campaigns SET deleted_at = updated_at WHERE is_deleted;
-- 의존하는 부분 인덱스 ix_campaigns_active_project_id가 함께 사라진다.
ALTER TABLE campaigns DROP COLUMN is_deleted;

ALTER TABLE projects RENAME COLUMN archived_at TO deleted_at;

-- V32/V33의 정의를 그대로 두고 술어만 교체해 재생성한다.
CREATE INDEX ix_links_active_campaign_id
    ON links (campaign_id, id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_links_active_project_id
    ON links (project_id, id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_campaigns_active_project_id
    ON campaigns (project_id, id DESC)
    WHERE deleted_at IS NULL;

-- 삭제된 링크가 키를 계속 점유하면, soft delete로 조회되지 않는 행 때문에
-- 재요청이 INSERT 충돌로만 실패해 원인을 알 수 없게 된다. 삭제 시 키를 푼다.
DROP INDEX uq_links_api_key_idempotency;
CREATE UNIQUE INDEX uq_links_api_key_idempotency
    ON links (idempotency_api_key_id, idempotency_key)
    WHERE idempotency_api_key_id IS NOT NULL AND idempotency_key IS NOT NULL AND deleted_at IS NULL;

DROP INDEX uq_links_campaign_external_id;
CREATE UNIQUE INDEX uq_links_campaign_external_id
    ON links (campaign_id, external_id)
    WHERE campaign_id IS NOT NULL AND external_id IS NOT NULL AND deleted_at IS NULL;

-- 단축 코드 유니크 인덱스(uq_links_base_code, uq_links_subdomain_code, uq_links_project_code)에는
-- 삭제 조건을 넣지 않는다. 넣으면 삭제된 링크의 코드가 재발급되어 이미 배포된 URL이 다른 곳을 가리킨다.
