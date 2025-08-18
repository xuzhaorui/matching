package io.github.xuzhaorui.matching.gatewayws.ws;

import io.github.xuzhaorui.matching.contracts.MatchRequest;
import io.github.xuzhaorui.matching.contracts.MatchResponse;
import io.github.xuzhaorui.matching.contracts.MatchServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MatchWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MatchWebSocketHandler.class);
    private final MatchServiceGrpc.MatchServiceBlockingStub stub;

    public MatchWebSocketHandler() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();
        this.stub = MatchServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        MatchRequest request = MatchRequest.newBuilder().setUserId(message.getPayload()).build();
        MatchResponse response = stub.join(request);
        session.sendMessage(new TextMessage(response.getMatchId()));
    }
}
