package link.srrrg.common;

/**
 * 쿠키 인증 웹 API({@code /api/web/**})와 비인증 공개 링크 API({@code /api/links/**}, {@code /{code}})가
 * 공유하는 오류 본문이다. API 키 표면({@code /api/v1/**})은 RFC 7807 {@code ProblemDetail}을 쓰므로
 * 이 형식을 사용하지 않는다. 두 형식의 공존은 의도된 것이며, 한 표면의 오류 형식을 바꿀 때 다른 표면까지 맞추지 않는다.
 *
 * @param code 클라이언트가 분기 조건으로 쓰는 안정적인 오류 식별자. 메시지와 달리 문구를 바꾸지 않는다
 * @param message 사용자에게 그대로 노출해도 되는 한국어 설명. 내부 예외 정보를 담지 않는다
 */
public record ApiErrorResponse(String code, String message) {
}
