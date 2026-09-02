package link.srrrg.auth;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 필터 단계에서 RFC 7807 형식의 오류 응답을 직접 쓴다.
 *
 * <p>요청이 컨트롤러에 닿기 전이라 {@code @ExceptionHandler}가 동작하지 않으므로 메시지 컨버터를 쓸 수 없다.
 * JSON을 문자열로 조립하는 만큼 값에 대한 이스케이프가 없으니, 여기에 넘기는 code와 detail은
 * 코드에 적힌 고정 문구여야 한다. 사용자 입력이나 예외 원문을 그대로 넘기면 응답 본문이 깨진다.</p>
 */
final class ApiProblemWriter {
	private ApiProblemWriter() {
	}

	static void write(HttpServletResponse response, int status, String code, String detail) throws IOException {
		String requestId = UUID.randomUUID().toString();
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setHeader("X-Request-Id", requestId);
		response.getWriter()
				.write("{\"type\":\"https://srrrg.link/problems/" + code.toLowerCase()
						+ "\",\"title\":\"API request failed\",\"status\":" + status + ",\"detail\":\"" + detail
						+ "\",\"code\":\"" + code + "\",\"requestId\":\"" + requestId + "\"}");
	}
}
