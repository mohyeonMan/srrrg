package link.srrrg.link;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import link.srrrg.link.dto.CreateLinkRequest;
import link.srrrg.link.dto.CreateLinkResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/links")
@Tag(name = "Links", description = "단축 링크 관리 API")
@RequiredArgsConstructor
public class LinkController {

	private final LinkService linkService;

	@PostMapping
	@Operation(summary = "단축 링크 생성", description = "원본 URL을 등록하고 단축 URL과 관리용 secret key를 발급합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "생성 성공"),
			@ApiResponse(responseCode = "400", description = "잘못된 URL 또는 만료 시각")
	})
	public ResponseEntity<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(linkService.create(request));
	}
}
