package com.busantiming.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tourism_concentration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourismPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_ymd", nullable = false)
    private LocalDate baseYmd;

    @Column(name = "area_cd", nullable = false, length = 10)
    private String areaCd;

    @Column(name = "area_nm", length = 50)
    private String areaNm;

    @Column(name = "signgu_cd", nullable = false, length = 10)
    private String signguCd;

    @Column(name = "signgu_nm", length = 50)
    private String signguNm;

    @Column(name = "tour_attraction_name", nullable = false, length = 100)
    private String tourAttractionName;

    @Column(name = "cnctr_rate")
    private Double cnctrRate;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
