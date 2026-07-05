package com.busantiming.sync;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "places", schema = "busan_timing_api")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", length = 50)
    private String contentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "district_name", nullable = false, length = 50)
    private String districtName;

    @Column(name = "district_code", nullable = false, length = 20)
    private String districtCode;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 100)
    private String theme;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "last_7_days_detail_view_count", nullable = false)
    private int last7DaysDetailViewCount;

    @Column(name = "monthly_detail_view_count", nullable = false)
    private int monthlyDetailViewCount;

    @Column(name = "monthly_average_congestion_score", nullable = false)
    private int monthlyAverageCongestionScore;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
