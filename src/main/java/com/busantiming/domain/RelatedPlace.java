package com.busantiming.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** TarRlteTarService1 areaBasedList1 원본 데이터. 값을 최대한 그대로 저장한다. */
@Entity
@Table(name = "related_place")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelatedPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_ym", length = 10)
    private String baseYm;

    @Column(name = "t_ats_cd", length = 64)
    private String tAtsCd;

    @Column(name = "t_ats_nm", length = 300)
    private String tAtsNm;

    @Column(name = "area_cd", length = 10)
    private String areaCd;

    @Column(name = "signgu_cd", length = 10)
    private String signguCd;

    @Column(name = "signgu_nm", length = 50)
    private String signguNm;

    @Column(name = "rlte_tats_cd", length = 64)
    private String rlteTatsCd;

    @Column(name = "rlte_tats_nm", length = 300)
    private String rlteTatsNm;

    @Column(name = "rlte_regn_cd", length = 10)
    private String rlteRegnCd;

    @Column(name = "rlte_signgu_cd", length = 10)
    private String rlteSignguCd;

    @Column(name = "rlte_signgu_nm", length = 50)
    private String rlteSignguNm;

    @Column(name = "rlte_ctgry_lcls_nm", length = 100)
    private String rlteCtgryLclsNm;

    @Column(name = "rlte_ctgry_mcls_nm", length = 100)
    private String rlteCtgryMclsNm;

    @Column(name = "rlte_ctgry_scls_nm", length = 100)
    private String rlteCtgrySclsNm;

    @Column(name = "rlte_rank", length = 10)
    private String rlteRank;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
