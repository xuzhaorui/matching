package io.github.xuzhaorui.matching.matchshard;

import io.github.xuzhaorui.matching.matchshard.service.MatchServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MatchShardApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchShardApplication.class, args);
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public Server grpcServer(MatchServiceImpl service) {
        return ServerBuilder.forPort(9090).addService(service).build();
    }
}
