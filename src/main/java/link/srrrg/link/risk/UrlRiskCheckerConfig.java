package link.srrrg.link.risk;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UrlRiskCheckerProperties.class)
class UrlRiskCheckerConfig {
}
