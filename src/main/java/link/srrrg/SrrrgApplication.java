package link.srrrg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 애플리케이션 진입점.
 *
 * <p>스케줄된 작업이 하나도 없어 {@code @EnableScheduling}을 붙이지 않는다. CSV 임포트를 요청
 * 트랜잭션 안에서 처리하도록 바꾸면서 마지막 {@code @Scheduled} worker가 사라졌다.
 * 다시 붙일 때는 스케줄러가 파드마다 독립적으로 돈다는 전제를 지켜야 한다. 같은 작업이 모든 파드에서
 * 동시에 실행되므로, 중복 실행을 막는 책임은 스케줄러가 아니라 각 작업(DB 제약이나 공유 저장소)에 있다.</p>
 *
 * <p>{@code @ConfigurationPropertiesScan}이 {@code @ConfigurationProperties} 레코드들을 빈으로 올린다.
 * 각 설정 클래스에 별도 등록이 없는 이유다.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SrrrgApplication {

	public static void main(String[] args) {
		SpringApplication.run(SrrrgApplication.class, args);
	}
}
