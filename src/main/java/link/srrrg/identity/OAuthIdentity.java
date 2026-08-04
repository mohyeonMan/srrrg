package link.srrrg.identity;

public record OAuthIdentity(
		OAuthProvider provider,
		String providerUserId,
		String providerEmail,
		boolean providerEmailVerified,
		String displayName
) {
	public String verifiedEmail() {
		return providerEmailVerified ? providerEmail : null;
	}
}
