package com.busantiming.sync;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * 연관관광지 API의 기준연월(baseYm) 계산. 데이터는 매월 8일 갱신되므로
 * 오늘이 8일 이후면 이번 달, 아니면 전월을 기준연월(YYYYMM)로 사용한다.
 */
public final class RelatedPlaceBaseYm {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int UPDATE_DAY = 8;

    private RelatedPlaceBaseYm() {
    }

    public static String resolve(LocalDate today) {
        YearMonth ym = YearMonth.from(today);
        if (today.getDayOfMonth() < UPDATE_DAY) {
            ym = ym.minusMonths(1);
        }
        return ym.format(YYYYMM);
    }

    public static String previousMonthOf(String baseYm) {
        return YearMonth.parse(baseYm, YYYYMM).minusMonths(1).format(YYYYMM);
    }
}
