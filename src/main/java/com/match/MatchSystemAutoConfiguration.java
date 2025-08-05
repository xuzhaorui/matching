package com.match;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(MatchProperties.class)
public class MatchSystemAutoConfiguration {

   @Bean
    public DisruptorNotificationService disruptorNotificationService() {
        return new DisruptorNotificationService(1 << 16 , 10);
    }


    @Bean
    public EnhancedMatchEngine enhancedMatchEngine(DisruptorNotificationService disruptorNotificationService,
                                   MatchProperties props,  ThreadPoolTaskExecutor matchThreadPool) {
        return new EnhancedMatchEngine(props, disruptorNotificationService, matchThreadPool);
    }


}
