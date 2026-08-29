package com.busantiming.sync;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 연관관광지 이름 + 구코드를 기존 {@code places}의 내부 id(place_id)로 매칭한다.
 * 정규화된 이름으로 인덱싱하고, 동명이 여럿이면 구코드로 구분한다.
 * (연관관광지 API는 표준 contentId를 주지 않으므로 이름 기반 매칭을 사용)
 */
public class PlaceIdResolver {

    private final List<SyncPlace> places;
    private final Map<String, List<SyncPlace>> byNormalizedName;

    public PlaceIdResolver(List<SyncPlace> places) {
        this.places = List.copyOf(places);
        this.byNormalizedName = this.places.stream()
                .filter(p -> p.getName() != null)
                .collect(Collectors.groupingBy(p -> SyncDataTransformer.normalizeForMatching(p.getName())));
    }

    public Optional<Long> resolve(String name, String districtCode) {
        if (name == null || name.isBlank()) return Optional.empty();
        List<SyncPlace> candidates = byNormalizedName.get(SyncDataTransformer.normalizeForMatching(name));
        if (candidates == null || candidates.isEmpty()) return Optional.empty();
        if (candidates.size() == 1) return Optional.of(candidates.get(0).getId());
        return candidates.stream()
                .filter(p -> Objects.equals(p.getDistrictCode(), districtCode))
                .map(SyncPlace::getId)
                .findFirst();
    }

    public Optional<Long> resolveFestivalVenue(String address, String districtCode) {
        if (address == null || address.isBlank()) return Optional.empty();

        String normalizedAddress = normalizeAddress(address);
        if (normalizedAddress.isBlank()) return Optional.empty();

        List<SyncPlace> candidates = places.stream()
                .filter(p -> Objects.equals(p.getDistrictCode(), districtCode))
                .filter(p -> p.getContentTypeId() != 15)
                .toList();

        Optional<Long> byNameInAddress = candidates.stream()
                .filter(p -> containsNormalized(normalizedAddress, p.getName()))
                .sorted(Comparator
                        .comparingInt((SyncPlace p) -> SyncDataTransformer.normalizeForMatching(p.getName()).length())
                        .reversed()
                        .thenComparing(SyncPlace::getId))
                .map(SyncPlace::getId)
                .findFirst();
        if (byNameInAddress.isPresent()) return byNameInAddress;

        return candidates.stream()
                .filter(p -> addressOverlaps(normalizedAddress, p.getAddress()))
                .sorted(Comparator.comparing(SyncPlace::getId))
                .map(SyncPlace::getId)
                .findFirst();
    }

    private static boolean containsNormalized(String normalizedText, String value) {
        if (value == null || value.isBlank()) return false;
        String normalizedValue = SyncDataTransformer.normalizeForMatching(value);
        return !normalizedValue.isBlank() && normalizedText.contains(normalizedValue);
    }

    private static boolean addressOverlaps(String normalizedAddress, String placeAddress) {
        if (placeAddress == null || placeAddress.isBlank()) return false;
        String normalizedPlaceAddress = normalizeAddress(placeAddress);
        return !normalizedPlaceAddress.isBlank()
                && (normalizedAddress.contains(normalizedPlaceAddress)
                || normalizedPlaceAddress.contains(normalizedAddress));
    }

    private static String normalizeAddress(String address) {
        return SyncDataTransformer.normalizeForMatching(address)
                .replace("일원", "");
    }
}
