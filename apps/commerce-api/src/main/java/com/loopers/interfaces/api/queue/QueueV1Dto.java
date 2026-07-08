package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueuePositionInfo;

public class QueueV1Dto {

    public record EnterResponse(long position, long totalWaiting) {

        public static EnterResponse from(QueuePositionInfo queuePositionInfo) {
            return new EnterResponse(queuePositionInfo.position(), queuePositionInfo.totalWaiting());
        }
    }

    public record PositionResponse(long position, long totalWaiting) {

        public static PositionResponse from(QueuePositionInfo queuePositionInfo) {
            return new PositionResponse(queuePositionInfo.position(), queuePositionInfo.totalWaiting());
        }
    }
}
