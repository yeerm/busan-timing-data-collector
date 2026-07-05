package com.busantiming.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface SyncCongestionForecastRepository extends JpaRepository<SyncCongestionForecast, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO busan_timing_api.congestion_forecasts (place_id, forecast_date, congestion_score, created_at, updated_at)
            VALUES (:placeId, :forecastDate, :congestionScore, now(), now())
            ON CONFLICT (place_id, forecast_date)
            DO UPDATE SET congestion_score = :congestionScore, updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("placeId") Long placeId,
                @Param("forecastDate") LocalDate forecastDate,
                @Param("congestionScore") int congestionScore);
}
