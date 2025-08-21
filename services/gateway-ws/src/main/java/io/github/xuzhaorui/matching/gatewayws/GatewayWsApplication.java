package io.github.xuzhaorui.matching.gatewayws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.github.xuzhaorui.matching")
public class GatewayWsApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayWsApplication.class, args);
    }
}
