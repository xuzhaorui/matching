package io.github.xuzhaorui.matching.notifierservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.github.xuzhaorui.matching")
public class NotifierServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotifierServiceApplication.class, args);
    }
}
