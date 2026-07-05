package com.busantiming.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SyncPlaceRepository extends JpaRepository<SyncPlace, Long> {

    Optional<SyncPlace> findByContentId(String contentId);

    @Modifying
    @Query(value = """
            UPDATE busan_timing_api.places p
            SET monthly_average_congestion_score = COALESCE(
                (SELECT CAST(ROUND(AVG(cf.congestion_score)) AS integer)
                 FROM busan_timing_api.congestion_forecasts cf
                 WHERE cf.place_id = p.id
                   AND cf.forecast_date >= CURRENT_DATE - INTERVAL '30 days'), 0),
                updated_at = now()
            WHERE p.active = true
            """, nativeQuery = true)
    void updateMonthlyAverageCongestionScores();
}
