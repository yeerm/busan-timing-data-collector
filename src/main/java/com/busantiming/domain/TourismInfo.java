package com.busantiming.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tourism_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourismInfo {

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
