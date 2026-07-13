package link.srrrg.link;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
class RedirectTicketManager {

	private static final Duration TICKET_TTL = Duration.ofMinutes(5);
	private static final String HMAC_SHA_256 = "HmacSHA256";

	String issue(String code, String originalUrl, String secretKeyHash, RedirectGrant grant) {
		long expiresAt = Instant.now().plus(TICKET_TTL).getEpochSecond();
		String urlHash = urlHash(originalUrl);
		String payload = payload(code, urlHash, grant, expiresAt);
		String signature = sign(secretKeyHash, payload);
		return expiresAt + "." + grant.name() + "." + urlHash + "." + signature;
	}

	Validation validate(String code, String originalUrl, String secretKeyHash, String ticket) {
		ParsedTicket parsedTicket = parse(ticket);
		if (parsedTicket == null) {
			return Validation.rejected(RejectReason.BAD_FORMAT);
		}
		if (Instant.now().isAfter(Instant.ofEpochSecond(parsedTicket.expiresAt()))) {
			return Validation.rejected(RejectReason.EXPIRED);
		}
		if (!constantTimeEquals(urlHash(originalUrl), parsedTicket.urlHash())) {
			return Validation.rejected(RejectReason.URL_CHANGED);
		}

		String payload = payload(code, parsedTicket.urlHash(), parsedTicket.grant(), parsedTicket.expiresAt());
		String expectedSignature = sign(secretKeyHash, payload);
		if (!constantTimeEquals(expectedSignature, parsedTicket.signature())) {
			return Validation.rejected(RejectReason.BAD_SIGNATURE);
		}
		return Validation.accepted(parsedTicket.grant());
	}

	private ParsedTicket parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String[] parts = value.split("\\.", -1);
		if (parts.length != 4) {
			return null;
		}
		try {
			return new ParsedTicket(Long.parseLong(parts[0]), RedirectGrant.valueOf(parts[1]), parts[2], parts[3]);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private String payload(String code, String urlHash, RedirectGrant grant, long expiresAt) {
		return code + "." + urlHash + "." + grant.name() + "." + expiresAt;
	}

	private String urlHash(String url) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String sign(String secret, String payload) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA_256);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
			return Base64.getUrlEncoder().withoutPadding()
					.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Redirect ticket signing failed", exception);
		}
	}

	private boolean constantTimeEquals(String left, String right) {
		return left != null && right != null
				&& MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
						right.getBytes(StandardCharsets.US_ASCII));
	}

	enum RedirectGrant {
		SAFE,
		MANUAL
	}

	enum RejectReason {
		BAD_FORMAT,
		EXPIRED,
		URL_CHANGED,
		BAD_SIGNATURE
	}

	record Validation(boolean accepted, RedirectGrant grant, RejectReason rejectReason) {
		private static Validation accepted(RedirectGrant grant) {
			return new Validation(true, grant, null);
		}

		private static Validation rejected(RejectReason reason) {
			return new Validation(false, null, reason);
		}
	}

	private record ParsedTicket(long expiresAt, RedirectGrant grant, String urlHash, String signature) {
	}
}
