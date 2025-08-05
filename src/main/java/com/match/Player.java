package com.match;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
public class Player {
    @Override
    public String toString() {
        return username + ":" + score + ":" + matchRange + ":" + mode;
    }

    private volatile String username;
    private volatile int score;  // 改为基本类型提升性能
    private volatile int matchRange;
    private volatile String mode;
    @Setter
    private volatile String channelId;

    @Builder
    public Player(String username, int score, int matchRange, String mode,
                  String channelId) {
        this.username = username;
        this.score = score;
        this.matchRange = matchRange;
        this.mode = mode;
        this.channelId = channelId;
    }

    public Player(String username, int score, int matchRange, String mode) {
        this.username = username;
        this.score = score;
        this.matchRange = matchRange;
        this.mode = mode;
    }

    public Player() {

    }


}
