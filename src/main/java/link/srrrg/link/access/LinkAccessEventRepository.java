package link.srrrg.link.access;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 접근 이벤트를 적재한다. 조회 메서드가 없는 것은 통계가 이 표를 JPA가 아니라
 * {@code StatisticsQueryRepository}의 네이티브 집계 쿼리로 읽기 때문이다.
 */
public interface LinkAccessEventRepository extends JpaRepository<LinkAccessEvent, Long> {
}
