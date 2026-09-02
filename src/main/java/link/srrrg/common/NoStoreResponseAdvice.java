package link.srrrg.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 본문이 있는 모든 컨트롤러 응답에 {@code Cache-Control: no-store}를 붙인다.
 *
 * <p>대상을 좁히지 않고 전부 적용하는 이유는, 응답 대부분이 로그인 사용자나 secret key 보유자에게만
 * 보여야 하는 데이터인데 어떤 엔드포인트를 빠뜨렸는지 사람이 관리하기 어렵기 때문이다.
 * 캐시해도 되는 응답이 생길 때 예외를 추가하는 편이, 캐시하면 안 되는 응답을 빠뜨리는 것보다 안전하다.</p>
 *
 * <p>{@code ResponseBodyAdvice}는 메시지 컨버터가 본문을 쓸 때만 호출된다. 본문이 없는
 * {@code /{code}} 리다이렉트 응답은 여기를 거치지 않으므로 {@code RedirectController}가 직접 헤더를 넣는다.</p>
 */
@ControllerAdvice
public class NoStoreResponseAdvice implements ResponseBodyAdvice<Object> {

	@Override
	public boolean supports(MethodParameter returnType,
			Class<? extends HttpMessageConverter<?>> converterType) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType,
			ServerHttpRequest request, ServerHttpResponse response) {
		// 관리 API 응답이 브라우저나 중간 캐시에 남지 않게 함.
		response.getHeaders().setCacheControl(CacheControl.noStore());
		return body;
	}
}
