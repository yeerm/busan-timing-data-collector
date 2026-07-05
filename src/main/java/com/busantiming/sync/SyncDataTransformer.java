package com.busantiming.sync;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SyncDataTransformer {

    private static final Pattern BUSAN_DISTRICT_PATTERN = Pattern.compile("부산(?:광역시)?\\s+(\\S+[구군])");
    private static final String DEFAULT_IMAGE_URL = "https://via.placeholder.com/400x300?text=No+Image";

    private static final Map<String, String> CATEGORY_MAP = Map.of(
            "12", "관광지",
            "14", "문화시설",
            "15", "축제공연행사",
            "25", "여행코스",
            "28", "레포츠",
            "32", "숙박",
            "38", "쇼핑",
            "39", "음식점"
    );

    private SyncDataTransformer() {
    }

    public static String normalizeForMatching(String name) {
        if (name == null) return "";
        return name.trim()
                .replace("㈜", "")
                .replaceAll("\\s+", "");
    }

    public static String extractDistrictName(String addr1) {
        if (addr1 == null || addr1.isBlank()) return "";
        Matcher matcher = BUSAN_DISTRICT_PATTERN.matcher(addr1);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static String mapCategory(String contentTypeId) {
        if (contentTypeId == null) return "기타";
        return CATEGORY_MAP.getOrDefault(contentTypeId, "기타");
    }

    public static Integer clampCongestionScore(Double cnctrRate) {
        if (cnctrRate == null) return null;
        int rounded = (int) Math.round(cnctrRate);
        return Math.max(0, Math.min(100, rounded));
    }

    public static String buildAddress(String addr1, String addr2) {
        boolean hasAddr1 = addr1 != null && !addr1.isBlank();
        boolean hasAddr2 = addr2 != null && !addr2.isBlank();

        if (!hasAddr1 && !hasAddr2) return "주소 정보 없음";
        if (!hasAddr2) return addr1.trim();
        return addr1.trim() + " " + addr2.trim();
    }

    public static String resolveImageUrl(String firstImage, String firstImage2) {
        if (firstImage != null && !firstImage.isBlank()) return firstImage;
        if (firstImage2 != null && !firstImage2.isBlank()) return firstImage2;
        return DEFAULT_IMAGE_URL;
    }

    public static double parseCoordinate(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
