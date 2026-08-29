package com.busantiming.sync;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class PlaceIdResolverTest {

    private SyncPlace place(long id, String name, String districtCode) {
        return place(id, name, districtCode, "");
    }

    private SyncPlace place(long id, String name, String districtCode, String address) {
        return place(id, name, districtCode, address, 12);
    }

    private SyncPlace place(long id, String name, String districtCode, String address, int contentTypeId) {
        return SyncPlace.builder()
                .id(id)
                .name(name)
                .districtCode(districtCode)
                .address(address)
                .contentTypeId(contentTypeId)
                .build();
    }

    @Test
    void resolvesByExactNameWhenUnique() {
        PlaceIdResolver resolver = new PlaceIdResolver(List.of(place(1L, "동백섬", "26350")));
        assertEquals(Optional.of(1L), resolver.resolve("동백섬", "26350"));
    }

    @Test
    void disambiguatesDuplicateNamesByDistrict() {
        PlaceIdResolver resolver = new PlaceIdResolver(List.of(
                place(1L, "해수욕장", "26350"),
                place(2L, "해수욕장", "26500")));
        assertEquals(Optional.of(2L), resolver.resolve("해수욕장", "26500"));
    }

    @Test
    void matchesIgnoringWhitespace() {
        PlaceIdResolver resolver = new PlaceIdResolver(List.of(place(1L, "광안리 해수욕장", "26500")));
        assertEquals(Optional.of(1L), resolver.resolve("광안리해수욕장", "26500"));
    }

    @Test
    void returnsEmptyWhenNotFound() {
        PlaceIdResolver resolver = new PlaceIdResolver(List.of(place(1L, "동백섬", "26350")));
        assertTrue(resolver.resolve("황금들밥/오크밸리월송점", "26350").isEmpty());
    }

    @Test
    void resolvesFestivalVenueByPlaceNameInFestivalAddressWithinSameDistrict() {
        PlaceIdResolver resolver = new PlaceIdResolver(List.of(
                place(1L, "광안리해수욕장", "26500", "부산광역시 수영구 광안해변로 219"),
                place(2L, "해운대해수욕장", "26350", "부산광역시 해운대구 우동")
        ));

        assertEquals(Optional.of(1L), resolver.resolveFestivalVenue("부산광역시 수영구 광안리해수욕장 일원", "26500"));
    }

    @Test
    void resolvesFestivalVenueByOverlappingAddressWithinSameDistrict() {
        PlaceIdResolver resolver = new PlaceIdResolver(List.of(
                place(1L, "부산시민공원", "26230", "부산광역시 부산진구 시민공원로 73")
        ));

        assertEquals(Optional.of(1L), resolver.resolveFestivalVenue("부산광역시 부산진구 시민공원로 73 일원", "26230"));
    }

    @Test
    void resolveFestivalVenueReturnsEmptyWhenAddressIsBlankOrNoSameDistrictMatch() {
        PlaceIdResolver resolver = new PlaceIdResolver(List.of(
                place(1L, "광안리해수욕장", "26500", "부산광역시 수영구 광안해변로 219")
        ));

        assertTrue(resolver.resolveFestivalVenue("", "26500").isEmpty());
        assertTrue(resolver.resolveFestivalVenue("부산광역시 해운대구 광안리해수욕장 일원", "26350").isEmpty());
    }

    @Test
    void resolveFestivalVenueIgnoresFestivalContentRows() {
        PlaceIdResolver resolver = new PlaceIdResolver(List.of(
                place(1L, "광안리어방축제", "26500", "부산광역시 수영구 광안해변로 219", 15),
                place(2L, "광안리해수욕장", "26500", "부산광역시 수영구 광안해변로 219", 12)
        ));

        assertEquals(Optional.of(2L), resolver.resolveFestivalVenue("부산광역시 수영구 광안해변로 219", "26500"));
    }
}
