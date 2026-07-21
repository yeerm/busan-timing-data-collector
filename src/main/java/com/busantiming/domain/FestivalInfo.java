package com.busantiming.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * KorService2 searchFestival2(행사정보조회) 원본 데이터. 들어오는 값을 최대한 그대로 저장한다.
 * (행사 기간은 원본 yyyyMMdd 문자열 그대로 보관 → 동기화 시 LocalDate로 변환)
 */
@Entity
@Table(name = "festival_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FestivalInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false, length = 20)
    private String contentId;

    @Column(name = "content_type_id", length = 10)
    private String contentTypeId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "addr1", length = 500)
    private String addr1;

    @Column(name = "addr2", length = 500)
    private String addr2;

    @Column(name = "zipcode", length = 10)
    private String zipcode;

    @Column(name = "tel", length = 100)
    private String tel;

    @Column(name = "first_image", length = 1000)
    private String firstImage;

    @Column(name = "first_image2", length = 1000)
    private String firstImage2;

    @Column(name = "mapx", length = 30)
    private String mapx;

    @Column(name = "mapy", length = 30)
    private String mapy;

    @Column(name = "event_start_date", length = 20)
    private String eventStartDate;

    @Column(name = "event_end_date", length = 20)
    private String eventEndDate;

    @Column(name = "l_dong_regn_cd", length = 10)
    private String lDongRegnCd;

    @Column(name = "l_dong_signgu_cd", length = 10)
    private String lDongSignguCd;

    @Column(name = "created_time", length = 20)
    private String createdTime;

    @Column(name = "modified_time", length = 20)
    private String modifiedTime;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
