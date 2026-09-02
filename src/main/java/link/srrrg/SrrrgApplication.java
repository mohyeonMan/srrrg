package link.srrrg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 진입점.
 *
 * <p>{@code @EnableScheduling}이 CSV 임포트 worker를 돌린다. 스케줄러는 파드마다 독립적으로 실행되므로,
 * 스케줄된 작업은 모든 파드에서 동시에 실행된다는 전제로 작성해야 한다. 중복 실행을 막는 책임은
 * 스케줄러가 아니라 각 작업(현재는 DB lease)에 있다.</p>
 *
 * <p>{@code @ConfigurationPropertiesScan}이 {@code @ConfigurationProperties} 레코드들을 빈으로 올린다.
 * 각 설정 클래스에 별도 등록이 없는 이유다.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SrrrgApplication {

	public static void main(String[] args) {
		SpringApplication.run(SrrrgApplication.class, args);
	}
}
