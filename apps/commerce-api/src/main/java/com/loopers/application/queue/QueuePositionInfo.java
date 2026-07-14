package com.loopers.application.queue;

public record QueuePositionInfo(long position, Long totalWaiting, Long estimatedWaitSeconds, String entryToken) {

    private static final long ISSUED_POSITION = 0L;

    public static QueuePositionInfo waiting(long position, long totalWaiting, long estimatedWaitSeconds) {
        return new QueuePositionInfo(position, totalWaiting, estimatedWaitSeconds, null);
    }

    public static QueuePositionInfo issued(String entryToken) {
        return new QueuePositionInfo(ISSUED_POSITION, null, null, entryToken);
    }
}
