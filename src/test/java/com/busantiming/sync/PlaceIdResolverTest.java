package com.busantiming.sync;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class PlaceIdResolverTest {

    private SyncPlace place(long id, String name, String districtCode) {
        return SyncPlace.builder().id(id).name(name).districtCode(districtCode).build();
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
}
