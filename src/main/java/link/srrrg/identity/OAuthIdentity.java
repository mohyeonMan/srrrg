package link.srrrg.identity;

/**
 * 공급자 응답에서 뽑아낸 신원 값의 묶음. 저장 전 정규화를 거치므로 이 타입만으로는 검증된 값이라고 볼 수 없다.
 */
public record OAuthIdentity(
		OAuthProvider provider,
		String providerUserId,
		String providerEmail,
		boolean providerEmailVerified,
		String displayName
) {
	/**
	 * 계정 매칭에 써도 되는 이메일만 돌려준다. 검증되지 않았으면 {@code null}이다.
	 * 이메일로 기존 사용자를 찾는 경로에서는 반드시 이 메서드를 거쳐야 한다.
	 */
	public String verifiedEmail() {
		return providerEmailVerified ? providerEmail : null;
	}
}
