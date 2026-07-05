package com.busantiming.sync;

import com.busantiming.domain.TourismInfo;
import lombok.Getter;

@Getter
public class PlaceMatchResult {

    private final TourismInfo tourismInfo;
    private final String matchType;
    private final String failureReason;

    private PlaceMatchResult(TourismInfo tourismInfo, String matchType, String failureReason) {
        this.tourismInfo = tourismInfo;
        this.matchType = matchType;
        this.failureReason = failureReason;
    }

    public static PlaceMatchResult matched(TourismInfo info, String matchType) {
        return new PlaceMatchResult(info, matchType, null);
    }

    public static PlaceMatchResult unmatched(String failureReason) {
        return new PlaceMatchResult(null, "NONE", failureReason);
    }

    public boolean isMatched() {
        return tourismInfo != null;
    }
}
