package link.srrrg.project.apikey.controller;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.project.apikey.model.ApiKeyScope;
import link.srrrg.project.apikey.model.ProjectApiKey;
import link.srrrg.project.apikey.service.ApiKeyService;
import lombok.RequiredArgsConstructor;

/** 프로젝트 API 키의 발급, 목록 조회와 폐기 계약을 담당한다. */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class ProjectApiKeyController {
	private final ApiKeyService apiKeyService;

	@GetMapping("/projects/{projectId}/api-keys")
	public List<ApiKeyResponse> apiKeys(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId) {
		return apiKeyService.list(principal.userId(), projectId).stream().map(ApiKeyResponse::from).toList();
	}

	@PostMapping("/projects/{projectId}/api-keys")
	public ResponseEntity<CreatedApiKeyResponse> createApiKey(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @Valid @RequestBody CreateApiKeyRequest request) {
		ApiKeyService.CreatedKey created = apiKeyService.create(principal.userId(), projectId, request.name(),
				request.scopes().stream().map(ApiKeyScope::fromValue).collect(Collectors.toSet()), request.expiresAt());
		return ResponseEntity.status(HttpStatus.CREATED).body(CreatedApiKeyResponse.from(created));
	}

	@DeleteMapping("/projects/{projectId}/api-keys/{keyId}")
	public ResponseEntity<Void> revokeApiKey(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @PathVariable Long keyId) {
		apiKeyService.revoke(principal.userId(), projectId, keyId);
		return ResponseEntity.noContent().build();
	}

	public record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name,
			@NotNull Set<@NotBlank String> scopes, Instant expiresAt) { }
	public record ApiKeyResponse(Long id, String name, String keyPrefix, Set<String> scopes, Instant createdAt,
			Instant lastUsedAt, Instant expiresAt, Instant revokedAt) {
		static ApiKeyResponse from(ProjectApiKey key) {
			return new ApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(),
					key.getScopes().stream().map(ApiKeyScope::value).collect(Collectors.toSet()), key.getCreatedAt(),
					key.getLastUsedAt(), key.getExpiresAt(), key.getRevokedAt());
		}
	}
	public record CreatedApiKeyResponse(Long id, String key, String keyPrefix, Set<String> scopes, Instant expiresAt) {
		static CreatedApiKeyResponse from(ApiKeyService.CreatedKey created) {
			ProjectApiKey key = created.key();
			return new CreatedApiKeyResponse(key.getId(), created.rawKey(), key.getKeyPrefix(),
					key.getScopes().stream().map(ApiKeyScope::value).collect(Collectors.toSet()), key.getExpiresAt());
		}
	}
}
