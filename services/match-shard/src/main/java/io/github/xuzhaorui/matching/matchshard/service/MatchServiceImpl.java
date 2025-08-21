package io.github.xuzhaorui.matching.matchshard.service;

import io.github.xuzhaorui.matching.contracts.MatchRequest;
import io.github.xuzhaorui.matching.contracts.MatchResponse;
import io.github.xuzhaorui.matching.contracts.MatchServiceGrpc;
import io.grpc.stub.StreamObserver;
import io.github.xuzhaorui.matching.messaging.MessagingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MatchServiceImpl extends MatchServiceGrpc.MatchServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(MatchServiceImpl.class);
    private final MessagingClient messagingClient;
    private final String sampleTopic;

    public MatchServiceImpl(MessagingClient messagingClient, @Value("${topics.sample}") String sampleTopic) {
        this.messagingClient = messagingClient;
        this.sampleTopic = sampleTopic;
    }

    @Override
    public void join(MatchRequest request, StreamObserver<MatchResponse> responseObserver) {
        log.info("join request: {}", request.getUserId());
        MatchResponse response = MatchResponse.newBuilder()
                .setMatchId("match-" + request.getUserId())
                .build();
        messagingClient.send(sampleTopic, response.getMatchId().getBytes());
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
