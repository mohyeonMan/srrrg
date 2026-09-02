package link.srrrg.project;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 초대 메일을 보낸다. 메일 설정이 없으면 예외를 던져 초대 생성 트랜잭션까지 롤백시킨다.
 *
 * <p>조용히 넘어가지 않는 것이 의도다. 발송에 실패했는데 초대만 만들어지면, 화면에는 초대가 보이지만
 * 상대는 아무것도 받지 못한 채 아무도 원인을 모른다.</p>
 *
 * <p>{@code ObjectProvider}로 받는 것은 메일 설정이 없는 환경에서도 애플리케이션이 뜨게 하기 위해서다.
 * 초대 기능을 쓰지 않는 배포에서 기동 자체가 막히지 않는다.</p>
 */
@Component
public class InvitationEmailSender {
	private final ObjectProvider<JavaMailSender> mailSender;
	private final String from;
	private final String host;

	public InvitationEmailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${srrrg.mail.from:}") String from,
			@Value("${spring.mail.host:}") String host) {
		this.mailSender = mailSender;
		this.from = from;
		this.host = host;
	}

	public void send(String email, String projectName, String invitationUrl) {
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null || host.isBlank())
			throw new IllegalStateException("초대 메일 발송 설정이 필요합니다.");
		if (from.isBlank())
			throw new IllegalStateException("초대 메일 발신 주소 설정이 필요합니다.");
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("srrrg 프로젝트 초대: " + projectName);
		message.setText(projectName + " 프로젝트에 초대되었습니다.\n\n초대 수락: " + invitationUrl);
		sender.send(message);
	}
}
