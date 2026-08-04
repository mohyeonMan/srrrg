package link.srrrg.auth;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

final class ApiProblemWriter {
	private ApiProblemWriter() { }
	static void write(HttpServletResponse response, int status, String code, String detail) throws IOException {
		String requestId = UUID.randomUUID().toString();
		response.setStatus(status); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE); response.setHeader("X-Request-Id", requestId);
		response.getWriter().write("{\"type\":\"https://srrrg.link/problems/" + code.toLowerCase() + "\",\"title\":\"API request failed\",\"status\":" + status + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\",\"requestId\":\"" + requestId + "\"}");
	}
}
