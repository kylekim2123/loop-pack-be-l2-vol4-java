package com.loopers.application.queue;

public record QueuePositionInfo(long position, long totalWaiting) {

    public static QueuePositionInfo of(long position, long totalWaiting) {
        return new QueuePositionInfo(position, totalWaiting);
    }
}
