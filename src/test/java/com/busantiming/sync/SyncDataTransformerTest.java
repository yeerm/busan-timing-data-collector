package com.busantiming.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SyncDataTransformerTest {

    @Test
    void normalizeForMatching_removesSpaces() {
        assertEquals("SEALIFE부산아쿠아리움",
                SyncDataTransformer.normalizeForMatching("SEA LIFE 부산아쿠아리움"));
    }

    @Test
    void normalizeForMatching_trimsWhitespace() {
        assertEquals("부산타워", SyncDataTransformer.normalizeForMatching("  부산타워  "));
    }

    @Test
    void normalizeForMatching_removesSpecialChars() {
        assertEquals("테스트", SyncDataTransformer.normalizeForMatching("㈜테스트"));
    }

    @Test
    void normalizeForMatching_nullReturnsEmpty() {
        assertEquals("", SyncDataTransformer.normalizeForMatching(null));
    }

    @ParameterizedTest
    @CsvSource({
            "'부산광역시 수영구 광안해변로 219', 수영구",
            "'부산광역시 해운대구 우동', 해운대구",
            "'부산광역시 사하구 다대동', 사하구",
            "'부산광역시 기장군 정관읍', 기장군",
            "'부산 해운대구 우동 510-7', 해운대구",
            "'부산 사상구 삼락동 29-46', 사상구",
            "'부산 강서구 대저1동 1-17', 강서구",
            "'부산 기장군 기장읍 대라리 72-3', 기장군",
    })
    void extractDistrictName_fromAddr1(String addr1, String expected) {
        assertEquals(expected, SyncDataTransformer.extractDistrictName(addr1));
    }

    @Test
    void extractDistrictName_nonBusanAddress() {
        assertEquals("", SyncDataTransformer.extractDistrictName("서울특별시 강남구"));
    }

    @Test
    void extractDistrictName_nullOrEmpty() {
        assertEquals("", SyncDataTransformer.extractDistrictName(null));
        assertEquals("", SyncDataTransformer.extractDistrictName(""));
    }

    @ParameterizedTest
    @CsvSource({
            "12, 관광지",
            "14, 문화시설",
            "15, 축제공연행사",
            "25, 여행코스",
            "28, 레포츠",
            "32, 숙박",
            "38, 쇼핑",
            "39, 음식점",
    })
    void mapCategory(String contentTypeId, String expected) {
        assertEquals(expected, SyncDataTransformer.mapCategory(contentTypeId));
    }

    @Test
    void mapCategory_unknownReturnsDefault() {
        assertEquals("기타", SyncDataTransformer.mapCategory("99"));
        assertEquals("기타", SyncDataTransformer.mapCategory(null));
    }

    @Test
    void clampCongestionScore_normalValues() {
        assertEquals(65, SyncDataTransformer.clampCongestionScore(65.0));
        assertEquals(66, SyncDataTransformer.clampCongestionScore(65.5));
        assertEquals(0, SyncDataTransformer.clampCongestionScore(0.0));
        assertEquals(100, SyncDataTransformer.clampCongestionScore(100.0));
    }

    @Test
    void clampCongestionScore_clampsOutOfRange() {
        assertEquals(100, SyncDataTransformer.clampCongestionScore(150.0));
        assertEquals(0, SyncDataTransformer.clampCongestionScore(-10.0));
    }

    @Test
    void clampCongestionScore_nullReturnsNull() {
        assertNull(SyncDataTransformer.clampCongestionScore(null));
    }

    @Test
    void buildAddress_combinesAddr1AndAddr2() {
        assertEquals("부산광역시 수영구 광안해변로 219 2층",
                SyncDataTransformer.buildAddress("부산광역시 수영구 광안해변로 219", "2층"));
    }

    @Test
    void buildAddress_addr1Only() {
        assertEquals("부산광역시 수영구",
                SyncDataTransformer.buildAddress("부산광역시 수영구", null));
        assertEquals("부산광역시 수영구",
                SyncDataTransformer.buildAddress("부산광역시 수영구", ""));
    }

    @Test
    void buildAddress_nonePresent() {
        assertEquals("주소 정보 없음", SyncDataTransformer.buildAddress(null, null));
        assertEquals("주소 정보 없음", SyncDataTransformer.buildAddress("", ""));
    }

    @Test
    void resolveImageUrl_prefersFirstImage() {
        assertEquals("http://img1.jpg",
                SyncDataTransformer.resolveImageUrl("http://img1.jpg", "http://img2.jpg"));
    }

    @Test
    void resolveImageUrl_fallsBackToSecondImage() {
        assertEquals("http://img2.jpg", SyncDataTransformer.resolveImageUrl(null, "http://img2.jpg"));
        assertEquals("http://img2.jpg", SyncDataTransformer.resolveImageUrl("", "http://img2.jpg"));
    }

    @Test
    void resolveImageUrl_defaultWhenBothEmpty() {
        String result = SyncDataTransformer.resolveImageUrl(null, null);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void parseCoordinate_validString() {
        assertEquals(35.1532, SyncDataTransformer.parseCoordinate("35.1532"), 0.0001);
    }

    @Test
    void parseCoordinate_invalidReturnsZero() {
        assertEquals(0.0, SyncDataTransformer.parseCoordinate(null));
        assertEquals(0.0, SyncDataTransformer.parseCoordinate(""));
        assertEquals(0.0, SyncDataTransformer.parseCoordinate("invalid"));
    }

    @Test
    void resolveDescription_usesOverviewWhenPresent() {
        assertEquals("광안리해수욕장은 부산의 대표 해변입니다.",
                SyncDataTransformer.resolveDescription("광안리해수욕장은 부산의 대표 해변입니다.", "광안리해수욕장"));
    }

    @Test
    void resolveDescription_trimsOverview() {
        assertEquals("설명 본문", SyncDataTransformer.resolveDescription("  설명 본문  ", "제목"));
    }

    @Test
    void resolveDescription_fallsBackWhenOverviewBlank() {
        assertEquals("광안리해수욕장 관광지 정보입니다.",
                SyncDataTransformer.resolveDescription("", "광안리해수욕장"));
        assertEquals("광안리해수욕장 관광지 정보입니다.",
                SyncDataTransformer.resolveDescription("   ", "광안리해수욕장"));
        assertEquals("광안리해수욕장 관광지 정보입니다.",
                SyncDataTransformer.resolveDescription(null, "  광안리해수욕장  "));
    }

    @Test
    void resolveDescription_emptyWhenNoOverviewAndNoTitle() {
        assertEquals("", SyncDataTransformer.resolveDescription(null, null));
        assertEquals("", SyncDataTransformer.resolveDescription("", null));
    }

    @Test
    void parseYyyyMmDd_validDate() {
        assertEquals(java.time.LocalDate.of(2026, 4, 3), SyncDataTransformer.parseYyyyMmDd("20260403"));
        assertEquals(java.time.LocalDate.of(2026, 12, 31), SyncDataTransformer.parseYyyyMmDd(" 20261231 "));
    }

    @Test
    void combineDistrictCode_combinesRegnAndSigngu() {
        assertEquals("26440", SyncDataTransformer.combineDistrictCode("26", "440"));
        assertEquals("26440", SyncDataTransformer.combineDistrictCode(" 26 ", " 440 "));
    }

    @Test
    void combineDistrictCode_nullWhenEitherMissing() {
        assertNull(SyncDataTransformer.combineDistrictCode(null, "440"));
        assertNull(SyncDataTransformer.combineDistrictCode("26", null));
        assertNull(SyncDataTransformer.combineDistrictCode("26", ""));
        assertNull(SyncDataTransformer.combineDistrictCode("", "440"));
    }

    @Test
    void parseYyyyMmDd_invalidReturnsNull() {
        assertNull(SyncDataTransformer.parseYyyyMmDd(null));
        assertNull(SyncDataTransformer.parseYyyyMmDd(""));
        assertNull(SyncDataTransformer.parseYyyyMmDd("2026"));
        assertNull(SyncDataTransformer.parseYyyyMmDd("invalid"));
        assertNull(SyncDataTransformer.parseYyyyMmDd("20261340"));
    }
}
