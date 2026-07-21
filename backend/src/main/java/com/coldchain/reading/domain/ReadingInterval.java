package com.coldchain.reading.domain;

import com.coldchain.common.error.SemanticInvalidException;

/**
 * readings 조회의 interval 파라미터 — 다운샘플 continuous aggregate 뷰 선택(M6 PR5).
 * viewName은 이 enum의 컴파일 타임 상수라 쿼리에 직접 넣어도 인젝션 위험이 없다(화이트리스트).
 * domain 패키지인 이유: controller·service·repository 셋 다 쓰는 값 개념이라, service에 두면
 * repository→service 역방향 의존이 생긴다(PR5 리뷰 후속으로 이동).
 */
public enum ReadingInterval {

    ONE_MINUTE("1m", "reading_1m"),
    FIVE_MINUTES("5m", "reading_5m");

    private final String param;
    private final String viewName;

    ReadingInterval(String param, String viewName) {
        this.param = param;
        this.viewName = viewName;
    }

    public String viewName() {
        return viewName;
    }

    /** 파라미터 문자열 → enum. 미지원 값은 422(SEMANTIC_INVALID). null은 호출부에서 원시 경로로 분기. */
    public static ReadingInterval fromParam(String value) {
        for (ReadingInterval interval : values()) {
            if (interval.param.equals(value)) {
                return interval;
            }
        }
        throw new SemanticInvalidException("interval은 1m 또는 5m만 지원합니다: " + value);
    }
}
