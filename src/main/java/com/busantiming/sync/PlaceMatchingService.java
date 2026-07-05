package com.busantiming.sync;

import com.busantiming.domain.TourismInfo;

import java.util.*;
import java.util.stream.Collectors;

public class PlaceMatchingService {

    private final Map<String, List<TourismInfo>> exactMap;
    private final Map<String, List<TourismInfo>> normalizedMap;

    public PlaceMatchingService(List<TourismInfo> tourismInfoList) {
        this.exactMap = tourismInfoList.stream()
                .filter(info -> info.getTitle() != null)
                .collect(Collectors.groupingBy(info -> info.getTitle().trim()));

        this.normalizedMap = tourismInfoList.stream()
                .filter(info -> info.getTitle() != null)
                .collect(Collectors.groupingBy(info -> SyncDataTransformer.normalizeForMatching(info.getTitle())));
    }

    public PlaceMatchResult match(String tourAttractionName, String signguNm) {
        if (tourAttractionName == null || tourAttractionName.isBlank()) {
            return PlaceMatchResult.unmatched("관광지명이 비어있습니다");
        }

        String trimmed = tourAttractionName.trim();
        List<TourismInfo> candidates = exactMap.getOrDefault(trimmed, List.of());
        if (!candidates.isEmpty()) {
            TourismInfo matched = pickByDistrict(candidates, signguNm);
            return PlaceMatchResult.matched(matched, "EXACT");
        }

        String normalized = SyncDataTransformer.normalizeForMatching(tourAttractionName);
        candidates = normalizedMap.getOrDefault(normalized, List.of());
        if (!candidates.isEmpty()) {
            TourismInfo matched = pickByDistrict(candidates, signguNm);
            return PlaceMatchResult.matched(matched, "NORMALIZED");
        }

        return PlaceMatchResult.unmatched("매칭 실패: tourism_info에 '" + tourAttractionName + "' 없음");
    }

    private TourismInfo pickByDistrict(List<TourismInfo> candidates, String signguNm) {
        if (candidates.size() == 1 || signguNm == null || signguNm.isBlank()) {
            return candidates.get(0);
        }

        return candidates.stream()
                .filter(info -> {
                    String district = SyncDataTransformer.extractDistrictName(info.getAddr1());
                    return signguNm.equals(district);
                })
                .findFirst()
                .orElse(candidates.get(0));
    }
}
