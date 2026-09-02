package link.srrrg.project;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

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
