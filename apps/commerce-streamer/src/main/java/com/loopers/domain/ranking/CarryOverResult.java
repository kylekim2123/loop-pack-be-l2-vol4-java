package com.loopers.domain.ranking;

public enum CarryOverResult {
    CARRIED_OVER,
    TARGET_ALREADY_EXISTS,
    SOURCE_MISSING;

    private static final long CARRIED_OVER_CODE = 1L;
    private static final long TARGET_ALREADY_EXISTS_CODE = -1L;
    private static final long SOURCE_MISSING_CODE = -2L;

    public static CarryOverResult from(long scriptReturnCode) {
        if (scriptReturnCode == CARRIED_OVER_CODE) {
            return CARRIED_OVER;
        }
        if (scriptReturnCode == TARGET_ALREADY_EXISTS_CODE) {
            return TARGET_ALREADY_EXISTS;
        }
        if (scriptReturnCode == SOURCE_MISSING_CODE) {
            return SOURCE_MISSING;
        }
        throw new IllegalStateException(String.format("알 수 없는 carry-over 스크립트 반환값입니다. code=%d", scriptReturnCode));
    }
}
