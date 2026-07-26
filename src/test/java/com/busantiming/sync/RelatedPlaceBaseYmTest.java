package com.busantiming.sync;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RelatedPlaceBaseYmTest {

    @Test
    void onOrAfter8th_usesCurrentMonth() {
        assertEquals("202607", RelatedPlaceBaseYm.resolve(LocalDate.of(2026, 7, 8)));
        assertEquals("202607", RelatedPlaceBaseYm.resolve(LocalDate.of(2026, 7, 27)));
    }

    @Test
    void before8th_usesPreviousMonth() {
        assertEquals("202606", RelatedPlaceBaseYm.resolve(LocalDate.of(2026, 7, 7)));
    }

    @Test
    void before8thInJanuary_rollsBackToPreviousDecember() {
        assertEquals("202512", RelatedPlaceBaseYm.resolve(LocalDate.of(2026, 1, 3)));
    }

    @Test
    void previousMonthOf_stepsBackOne() {
        assertEquals("202605", RelatedPlaceBaseYm.previousMonthOf("202606"));
        assertEquals("202512", RelatedPlaceBaseYm.previousMonthOf("202601"));
    }
}
