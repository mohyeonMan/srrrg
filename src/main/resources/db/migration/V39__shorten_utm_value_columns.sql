-- UTM 값 상한을 500자에서 100자로 줄인다. GA4의 campaign source·medium·name 상한과 같은 값이다.
--
-- 리다이렉트가 실제로 사용하는 주소는 목적지에 UTM 값을 합친 결과인데, 그 결과는 저장되지 않고
-- 매 요청 계산된다. 캠페인 기본값이 링크 생성 이후에도 바뀌므로 병합 결과 길이를 생성 시점에
-- 재는 것으로는 아무것도 보장하지 못한다. 활성 필드 수가 이미 10개로 묶여 있으니, 값 하나의 상한이
-- 전체 URL 길이 상한으로 합성되게 하는 쪽이 쓰기 시점에 성립한다.
--
-- 100자를 넘는 행이 있으면 이 migration은 실패한다. 조용히 잘라 내지 않는 것이 의도다.
ALTER TABLE link_utm_values
    ALTER COLUMN value TYPE VARCHAR(100);

ALTER TABLE campaign_utm_defaults
    ALTER COLUMN default_value TYPE VARCHAR(100);
