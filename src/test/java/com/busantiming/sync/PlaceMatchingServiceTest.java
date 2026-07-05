package com.busantiming.sync;

import com.busantiming.domain.TourismInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaceMatchingServiceTest {

    private PlaceMatchingService matchingService;

    @BeforeEach
    void setUp() {
        List<TourismInfo> tourismInfoList = List.of(
                createTourismInfo("C001", "해운대해수욕장", "부산광역시 해운대구 우동"),
                createTourismInfo("C002", "광안리해수욕장", "부산광역시 수영구 광안해변로 219"),
                createTourismInfo("C003", "SEA LIFE 부산아쿠아리움", "부산광역시 해운대구 해운대해변로 266"),
                createTourismInfo("C004", "태종대", "부산광역시 영도구 전망로 24")
        );
        matchingService = new PlaceMatchingService(tourismInfoList);
    }

    @Test
    void exactMatch_succeeds() {
        PlaceMatchResult result = matchingService.match("해운대해수욕장", "해운대구");
        assertTrue(result.isMatched());
        assertEquals("C001", result.getTourismInfo().getContentId());
        assertEquals("EXACT", result.getMatchType());
    }

    @Test
    void normalizedMatch_succeedsWithSpaceDifference() {
        PlaceMatchResult result = matchingService.match("SEA LIFE부산아쿠아리움", "해운대구");
        assertTrue(result.isMatched());
        assertEquals("C003", result.getTourismInfo().getContentId());
        assertEquals("NORMALIZED", result.getMatchType());
    }

    @Test
    void matchFails_returnsUnmatched() {
        PlaceMatchResult result = matchingService.match("존재하지않는관광지", "해운대구");
        assertFalse(result.isMatched());
        assertNotNull(result.getFailureReason());
    }

    @Test
    void districtVerification_correctDistrict() {
        PlaceMatchResult result = matchingService.match("태종대", "영도구");
        assertTrue(result.isMatched());
        assertEquals("C004", result.getTourismInfo().getContentId());
    }

    @Test
    void districtVerification_nullDistrictStillMatches() {
        PlaceMatchResult result = matchingService.match("태종대", null);
        assertTrue(result.isMatched());
        assertEquals("C004", result.getTourismInfo().getContentId());
    }

    @Test
    void duplicateNames_disambiguatedByDistrict() {
        List<TourismInfo> listWithDuplicates = List.of(
                createTourismInfo("D001", "시민공원", "부산광역시 부산진구 시민공원로 73"),
                createTourismInfo("D002", "시민공원", "부산광역시 해운대구 시민공원길 10")
        );
        PlaceMatchingService service = new PlaceMatchingService(listWithDuplicates);

        PlaceMatchResult result = service.match("시민공원", "해운대구");
        assertTrue(result.isMatched());
        assertEquals("D002", result.getTourismInfo().getContentId());
    }

    private TourismInfo createTourismInfo(String contentId, String title, String addr1) {
        return TourismInfo.builder()
                .contentId(contentId)
                .title(title)
                .addr1(addr1)
                .build();
    }
}
