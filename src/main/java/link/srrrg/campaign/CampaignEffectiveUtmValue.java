package link.srrrg.campaign;

/**
 * 캠페인 링크 목록에 표시할 유효 UTM 값이다. Repository projection을 Controller까지 노출하지 않고,
 * 링크 고유값과 캠페인 기본값 중 실제 적용되는 값과 그 출처만 전달한다.
 */
record CampaignEffectiveUtmValue(Long linkId, String fieldName, String value, String source) {
}
