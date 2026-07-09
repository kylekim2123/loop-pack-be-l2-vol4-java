package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueuePositionInfo;

public class QueueV1Dto {

    public record EnterResponse(long position, Long totalWaiting, Long estimatedWaitSeconds, String entryToken) {

        public static EnterResponse from(QueuePositionInfo queuePositionInfo) {
            return new EnterResponse(
                queuePositionInfo.position(),
                queuePositionInfo.totalWaiting(),
                queuePositionInfo.estimatedWaitSeconds(),
                queuePositionInfo.entryToken()
            );
        }
    }

    public record PositionResponse(long position, Long totalWaiting, Long estimatedWaitSeconds, String entryToken) {

        public static PositionResponse from(QueuePositionInfo queuePositionInfo) {
            return new PositionResponse(
                queuePositionInfo.position(),
                queuePositionInfo.totalWaiting(),
                queuePositionInfo.estimatedWaitSeconds(),
                queuePositionInfo.entryToken()
            );
        }
    }
}
