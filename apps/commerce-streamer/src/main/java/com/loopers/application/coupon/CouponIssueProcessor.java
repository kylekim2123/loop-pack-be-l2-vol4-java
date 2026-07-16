package com.loopers.application.coupon;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.event.CouponIssueRequestedEvent;
import com.loopers.domain.event.EventHandledModel;
import com.loopers.domain.event.EventHandledRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueProcessor {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SOLD_OUT_REASON = "쿠폰 수량이 모두 소진되었습니다.";
    private static final String DUPLICATED_REASON = "이미 발급받은 쿠폰입니다.";

    private static final String EXISTS_USER_COUPON_SQL = """
        SELECT EXISTS(SELECT 1 FROM user_coupons WHERE user_id = ? AND coupon_id = ?)
        """;
    private static final String INCREMENT_ISSUED_COUNT_SQL = """
        UPDATE coupons
        SET issued_count = issued_count + 1, updated_at = NOW(6)
        WHERE id = ? AND deleted_at IS NULL AND (max_quantity IS NULL OR issued_count < max_quantity)
        """;
    private static final String INSERT_USER_COUPON_SQL = """
        INSERT INTO user_coupons (user_id, coupon_id, name, discount_type, discount_value, min_order_amount, expired_at, version, created_at, updated_at)
        SELECT ?, c.id, c.name, c.discount_type, c.discount_value, c.min_order_amount, c.expired_at, 0, NOW(6), NOW(6)
        FROM coupons c
        WHERE c.id = ?
        """;
    private static final String UPDATE_REQUEST_STATUS_SQL = """
        UPDATE coupon_issue_requests
        SET status = ?, reason = ?, updated_at = NOW(6)
        WHERE id = ?
        """;

    private final EventHandledRepository eventHandledRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void process(CouponIssueRequestedEvent event) {
        if (eventHandledRepository.existsByEventId(event.eventId())) {
            return;
        }
        eventHandledRepository.save(EventHandledModel.from(event.eventId()));

        Long requestId = event.requestId();
        Long userId = event.userId();
        Long couponId = event.couponId();

        Boolean alreadyIssued = jdbcTemplate.queryForObject(EXISTS_USER_COUPON_SQL, Boolean.class, userId, couponId);
        if (Boolean.TRUE.equals(alreadyIssued)) {
            markRequest(requestId, STATUS_FAILED, DUPLICATED_REASON);
            return;
        }

        int incrementedRows = jdbcTemplate.update(INCREMENT_ISSUED_COUNT_SQL, couponId);
        if (incrementedRows == 0) {
            markRequest(requestId, STATUS_FAILED, SOLD_OUT_REASON);
            return;
        }

        int insertedRows = jdbcTemplate.update(INSERT_USER_COUPON_SQL, userId, couponId);
        if (insertedRows == 0) {
            throw new IllegalStateException(String.format("쿠폰 템플릿이 사라져 발급에 실패했습니다 (couponId=%d)", couponId));
        }

        markRequest(requestId, STATUS_SUCCESS, null);
    }

    private void markRequest(Long requestId, String status, String reason) {
        int updatedRows = jdbcTemplate.update(UPDATE_REQUEST_STATUS_SQL, status, reason, requestId);
        if (updatedRows == 0) {
            log.warn("발급 요청 상태 갱신 대상이 없습니다 (requestId={}, status={})", requestId, status);
        }
    }
}
