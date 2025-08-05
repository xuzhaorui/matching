package com.match;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Duration;


@ConfigurationProperties(prefix = "match")
@Data
public class MatchProperties {
    private int bucketSize = 10;
    private int maxScore = 100;
    private int expectedLoadPerBucket = 50000;

    private int maxRetries = 3;
    private Duration initialBackoff = Duration.ofMillis(100);

    // 最大并发通知数
    private int maxConcurrentNotifications = 200;

    /***
     * @Description 通知程序线程
     */
    private int notifierThreads = 4;

    /***
     * @Description 通告程序队列大小
     */
    private int notifierQueueSize = 1000;

    /***
     * @Description 最大背压缓冲器
     */
    private int maxBackpressureBuffer = 9000;
    /**
     * @Description 接收器缓冲区大小
     */
    private int sinkBufferSize = 1000;
}
