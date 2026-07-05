package com.busantiming.sync;

import java.util.Map;

public final class BusanDistrictCode {

    private static final Map<String, String> NAME_TO_CODE = Map.ofEntries(
            Map.entry("중구", "26110"),
            Map.entry("서구", "26140"),
            Map.entry("동구", "26170"),
            Map.entry("영도구", "26200"),
            Map.entry("부산진구", "26230"),
            Map.entry("동래구", "26260"),
            Map.entry("남구", "26290"),
            Map.entry("북구", "26320"),
            Map.entry("해운대구", "26350"),
            Map.entry("사하구", "26380"),
            Map.entry("금정구", "26410"),
            Map.entry("강서구", "26440"),
            Map.entry("연제구", "26470"),
            Map.entry("수영구", "26500"),
            Map.entry("사상구", "26530"),
            Map.entry("기장군", "26710")
    );

    private static final Map<String, String> CODE_TO_NAME = Map.ofEntries(
            Map.entry("26110", "중구"),
            Map.entry("26140", "서구"),
            Map.entry("26170", "동구"),
            Map.entry("26200", "영도구"),
            Map.entry("26230", "부산진구"),
            Map.entry("26260", "동래구"),
            Map.entry("26290", "남구"),
            Map.entry("26320", "북구"),
            Map.entry("26350", "해운대구"),
            Map.entry("26380", "사하구"),
            Map.entry("26410", "금정구"),
            Map.entry("26440", "강서구"),
            Map.entry("26470", "연제구"),
            Map.entry("26500", "수영구"),
            Map.entry("26530", "사상구"),
            Map.entry("26710", "기장군")
    );

    private BusanDistrictCode() {
    }

    public static String getCodeByName(String districtName) {
        if (districtName == null) return null;
        return NAME_TO_CODE.get(districtName);
    }

    public static String getNameByCode(String code) {
        if (code == null) return null;
        return CODE_TO_NAME.get(code);
    }

    public static boolean isValidName(String districtName) {
        return districtName != null && NAME_TO_CODE.containsKey(districtName);
    }
}
