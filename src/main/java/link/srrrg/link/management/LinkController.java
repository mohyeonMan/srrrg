package link.srrrg.link.management;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.dto.CreateLinkResponse;
import link.srrrg.link.management.dto.DeleteLinkResponse;
import link.srrrg.link.management.dto.LinkManagementResponse;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/links")
@Tag(name = "Links", description = "단축 링크 관리 API")
@RequiredArgsConstructor
public class LinkController {
	private static final String SECRET_KEY_HEADER = "X-Srrrg-Secret-Key";

	private final LinkManagementService linkService;

	@PostMapping
	@Operation(summary = "단축 링크 생성", description = "원본 URL을 등록하고 단축 URL과 관리용 secret key를 발급합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "생성 성공"),
			@ApiResponse(responseCode = "400", description = "잘못된 URL, 만료 시각 또는 위협 URL"),
			@ApiResponse(responseCode = "503", description = "URL 안전 검사 불가")
	})
	public ResponseEntity<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(linkService.create(request));
	}

	@GetMapping("/{code}")
	@Operation(summary = "단축 링크 관리 정보 조회", description = "단축 코드와 secret key로 링크 정보와 누적 통계를 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "400", description = "secret key 헤더 누락"),
			@ApiResponse(responseCode = "404", description = "링크 없음 또는 secret key 불일치"),
			@ApiResponse(responseCode = "410", description = "삭제된 링크")
	})
	public LinkManagementResponse getManagedLink(
			@PathVariable String code,
			@RequestHeader(SECRET_KEY_HEADER) String secretKey
	) {
		return linkService.getManagedLink(code, secretKey);
	}

	@PatchMapping("/{code}")
	@Operation(summary = "단축 링크 수정", description = "secret key로 인증한 뒤 원본 URL 또는 만료 시각을 수정합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "400", description = "잘못된 요청 또는 위협 URL"),
			@ApiResponse(responseCode = "404", description = "링크 없음 또는 secret key 불일치"),
			@ApiResponse(responseCode = "410", description = "삭제된 링크"),
			@ApiResponse(responseCode = "503", description = "URL 안전 검사 불가")
	})
	public LinkManagementResponse updateManagedLink(
			@PathVariable String code,
			@RequestHeader(SECRET_KEY_HEADER) String secretKey,
			@RequestBody UpdateLinkRequest request
	) {
		return linkService.updateManagedLink(code, secretKey, request);
	}

	@DeleteMapping("/{code}")
	@Operation(summary = "단축 링크 삭제", description = "secret key로 인증한 뒤 링크를 soft delete합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제 성공"),
			@ApiResponse(responseCode = "400", description = "secret key 헤더 누락"),
			@ApiResponse(responseCode = "404", description = "링크 없음 또는 secret key 불일치"),
			@ApiResponse(responseCode = "410", description = "이미 삭제된 링크")
	})
	public DeleteLinkResponse deleteManagedLink(
			@PathVariable String code,
			@RequestHeader(SECRET_KEY_HEADER) String secretKey
	) {
		return linkService.deleteManagedLink(code, secretKey);
	}
}
