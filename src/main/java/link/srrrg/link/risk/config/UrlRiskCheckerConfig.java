package link.srrrg.link.risk.config;


import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UrlRiskCheckerProperties.class)
public class UrlRiskCheckerConfig {
}
