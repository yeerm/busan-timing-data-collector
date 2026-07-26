# 연관관광지(related_places) 수집 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 한국관광공사 연관관광지 API(`TarRlteTarService1/areaBasedList1`)로 부산 16개 구의 "관광지 → 연관관광지" 관계를 수집해 원본 테이블에 저장하고, 이름+구 매칭으로 `places`와 연결한 서빙 테이블 `related_places`를 채운다.

**Architecture:** 기존 축제 수집 패턴을 그대로 따른다 — (1) `RelatedPlaceApiService`가 16개 구를 순회·페이징하며 API 호출 → (2) `RelatedPlaceBatchConfig` job이 원본 `related_place` 테이블을 통째 교체(deleteAll + saveAll) → (3) `ApiSyncBatchConfig`의 신규 step이 원본을 읽어 `places`를 이름+구코드로 매칭한 뒤 `busan_timing_api.related_places`(place_id, related_place_id)를 jdbcTemplate으로 스냅샷 교체.

**Tech Stack:** Spring Boot, Spring Batch, Spring Data JPA(원본 테이블 `ddl-auto: update` 자동생성), JdbcTemplate(서빙 upsert), JUnit 5. 서빙 테이블 `related_places`는 API 저장소 Flyway로 이미 생성됨.

---

## 사전 확인 (구현 시작 전)

- 서빙 테이블 `busan_timing_api.related_places`가 Flyway로 생성됐는지 확인. 스키마 가정: `id`(PK), `place_id`(bigint, FK→places.id), `related_place_id`(bigint, FK→places.id), `rlte_rank`(int), `created_at`, `updated_at`, `UNIQUE(place_id, related_place_id)`.
- 없으면 이 플랜의 서빙 sync(Task 6) 실행 시 에러 → 먼저 마이그레이션 반영 요청.

## File Structure

**신규 (수집기 저장소):**
- `src/main/java/com/busantiming/sync/RelatedPlaceBaseYm.java` — 기준연월 계산(순수 함수, 테스트 대상)
- `src/main/java/com/busantiming/sync/PlaceIdResolver.java` — 이름+구코드 → places.id 매칭(순수 로직, 테스트 대상)
- `src/main/java/com/busantiming/dto/RelatedPlaceResponse.java` — API 응답 DTO (`FestivalResponse` 미러)
- `src/main/java/com/busantiming/domain/RelatedPlace.java` — 원본 엔티티 (`FestivalInfo` 미러)
- `src/main/java/com/busantiming/domain/RelatedPlaceRepository.java`
- `src/main/java/com/busantiming/service/RelatedPlaceApiService.java` — API 호출 (`FestivalApiService` 미러)
- `src/main/java/com/busantiming/batch/RelatedPlaceBatchConfig.java` — 원본 수집 job (`FestivalInfoBatchConfig` 미러)
- `src/test/java/com/busantiming/sync/RelatedPlaceBaseYmTest.java`
- `src/test/java/com/busantiming/sync/PlaceIdResolverTest.java`

**수정:**
- `src/main/java/com/busantiming/batch/ApiSyncBatchConfig.java` — `RelatedPlaceRepository` 주입 + `syncRelatedPlacesStep` 추가 + `apiSyncJob` 체인에 연결
- `src/main/java/com/busantiming/batch/BatchRunController.java` — `relatedPlaceJob` 수동 실행 엔드포인트 추가

---

## Task 1: 기준연월(baseYm) 계산

**Files:**
- Create: `src/main/java/com/busantiming/sync/RelatedPlaceBaseYm.java`
- Test: `src/test/java/com/busantiming/sync/RelatedPlaceBaseYmTest.java`

연관 데이터는 매월 8일 갱신되므로, 오늘이 8일 이후면 이번 달, 아니면 전월을 기준연월(YYYYMM)로 쓴다.

- [ ] **Step 1: 실패 테스트 작성**

```java
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
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests "com.busantiming.sync.RelatedPlaceBaseYmTest"` → FAIL(클래스 없음)

- [ ] **Step 3: 최소 구현**

```java
package com.busantiming.sync;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class RelatedPlaceBaseYm {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int UPDATE_DAY = 8; // 매월 8일 갱신

    private RelatedPlaceBaseYm() {
    }

    public static String resolve(LocalDate today) {
        YearMonth ym = YearMonth.from(today);
        if (today.getDayOfMonth() < UPDATE_DAY) {
            ym = ym.minusMonths(1);
        }
        return ym.format(YYYYMM);
    }

    public static String previousMonthOf(String baseYm) {
        return YearMonth.parse(baseYm, YYYYMM).minusMonths(1).format(YYYYMM);
    }
}
```

- [ ] **Step 4: 통과 확인** — Run: `./gradlew test --tests "com.busantiming.sync.RelatedPlaceBaseYmTest"` → PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/busantiming/sync/RelatedPlaceBaseYm.java src/test/java/com/busantiming/sync/RelatedPlaceBaseYmTest.java
git commit -m "feat: 연관관광지 기준연월(baseYm) 계산 유틸 추가"
```

---

## Task 2: 이름+구코드 → place_id 매칭기

**Files:**
- Create: `src/main/java/com/busantiming/sync/PlaceIdResolver.java`
- Test: `src/test/java/com/busantiming/sync/PlaceIdResolverTest.java`

`places` 목록을 정규화된 이름으로 인덱싱하고, (이름, 구코드)로 place_id를 찾는다. 동명이 여럿이면 구코드로 구분, 단일이면 그대로, 못 찾으면 empty. `SyncDataTransformer.normalizeForMatching` 재사용.

- [ ] **Step 1: 실패 테스트 작성**

```java
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
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests "com.busantiming.sync.PlaceIdResolverTest"` → FAIL

- [ ] **Step 3: 최소 구현**

```java
package com.busantiming.sync;

import java.util.*;
import java.util.stream.Collectors;

public class PlaceIdResolver {

    private final Map<String, List<SyncPlace>> byNormalizedName;

    public PlaceIdResolver(List<SyncPlace> places) {
        this.byNormalizedName = places.stream()
                .filter(p -> p.getName() != null)
                .collect(Collectors.groupingBy(p -> SyncDataTransformer.normalizeForMatching(p.getName())));
    }

    public Optional<Long> resolve(String name, String districtCode) {
        if (name == null || name.isBlank()) return Optional.empty();
        List<SyncPlace> candidates = byNormalizedName.get(SyncDataTransformer.normalizeForMatching(name));
        if (candidates == null || candidates.isEmpty()) return Optional.empty();
        if (candidates.size() == 1) return Optional.of(candidates.get(0).getId());
        return candidates.stream()
                .filter(p -> Objects.equals(p.getDistrictCode(), districtCode))
                .map(SyncPlace::getId)
                .findFirst();
    }
}
```

- [ ] **Step 4: 통과 확인** — Run: `./gradlew test --tests "com.busantiming.sync.PlaceIdResolverTest"` → PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/busantiming/sync/PlaceIdResolver.java src/test/java/com/busantiming/sync/PlaceIdResolverTest.java
git commit -m "feat: 이름+구코드 → place_id 매칭기 추가"
```

---

## Task 3: 응답 DTO

**Files:**
- Create: `src/main/java/com/busantiming/dto/RelatedPlaceResponse.java`

`FestivalResponse` 구조 그대로. `tAtsCd`/`tAtsNm`는 Jackson이 `tatsCd`로 오해하지 않도록 `@JsonProperty` 명시(축제의 `lDongRegnCd`와 동일 이유).

- [ ] **Step 1: 작성**

```java
package com.busantiming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/** TarRlteTarService1 areaBasedList1(지역기반 연관관광지) 응답. */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelatedPlaceResponse {

    private Response response;

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private Header header;
        private Body body;
    }

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonSetter(nulls = Nulls.SKIP)
        private Object items;
        private int numOfRows;
        private int pageNo;
        private int totalCount;
    }

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {
        private List<Item> item;
    }

    @Getter @Setter @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String baseYm;
        @JsonProperty("tAtsCd")
        private String tAtsCd;
        @JsonProperty("tAtsNm")
        private String tAtsNm;
        private String areaCd;
        private String areaNm;
        private String signguCd;
        private String signguNm;
        private String rlteTatsCd;
        private String rlteTatsNm;
        private String rlteRegnCd;
        private String rlteRegnNm;
        private String rlteSignguCd;
        private String rlteSignguNm;
        private String rlteCtgryLclsNm;
        private String rlteCtgryMclsNm;
        private String rlteCtgrySclsNm;
        private String rlteRank;
    }
}
```

- [ ] **Step 2: 컴파일 확인** — Run: `./gradlew compileJava` → BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/busantiming/dto/RelatedPlaceResponse.java
git commit -m "feat: 연관관광지 응답 DTO 추가"
```

---

## Task 4: 원본 엔티티 + 리포지토리

**Files:**
- Create: `src/main/java/com/busantiming/domain/RelatedPlace.java`
- Create: `src/main/java/com/busantiming/domain/RelatedPlaceRepository.java`

원본은 API 값 그대로 저장(`FestivalInfo` 미러). `ddl-auto: update`로 `related_place` 테이블 자동 생성됨.

- [ ] **Step 1: 엔티티 작성**

```java
package com.busantiming.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** TarRlteTarService1 areaBasedList1 원본 데이터. 값을 최대한 그대로 저장한다. */
@Entity
@Table(name = "related_place")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
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
```

- [ ] **Step 2: 리포지토리 작성**

```java
package com.busantiming.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RelatedPlaceRepository extends JpaRepository<RelatedPlace, Long> {
}
```

- [ ] **Step 3: 컴파일 확인** — Run: `./gradlew compileJava` → BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/busantiming/domain/RelatedPlace.java src/main/java/com/busantiming/domain/RelatedPlaceRepository.java
git commit -m "feat: 연관관광지 원본 엔티티/리포지토리 추가"
```

---

## Task 5: API 호출 서비스 (16개 구 순회 + 페이징)

**Files:**
- Create: `src/main/java/com/busantiming/service/RelatedPlaceApiService.java`

`FestivalApiService` 미러. `TarRlteTarService1/areaBasedList1`, `areaCd=26` 고정, 16개 구(`BusanDistrictCode`) 순회, 구마다 페이징. baseYm은 `RelatedPlaceBaseYm.resolve(today)`; 특정 구 1페이지가 비면 그 구에 한해 전월로 1회 재시도.

- [ ] **Step 1: 작성**

```java
package com.busantiming.service;

import com.busantiming.dto.RelatedPlaceResponse;
import com.busantiming.dto.RelatedPlaceResponse.Item;
import com.busantiming.dto.RelatedPlaceResponse.Items;
import com.busantiming.sync.BusanDistrictCode;
import com.busantiming.sync.RelatedPlaceBaseYm;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** TarRlteTarService1 areaBasedList1로 부산 16개 구의 연관관광지를 수집한다. */
@Slf4j
@Service
public class RelatedPlaceApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${api.data-go-kr.service-key}")
    private String serviceKey;

    @Value("${api.data-go-kr.num-of-rows}")
    private int numOfRows;

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/TarRlteTarService1";
    private static final String BUSAN_AREA_CD = "26";

    // 각 Item에 어느 구에서 조회했는지(구코드)를 실어 나르기 위한 캐리어
    public record CollectedItem(String baseSignguCd, Item item) {}

    public RelatedPlaceApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** 16개 구 전체를 수집한다. */
    public List<CollectedItem> fetchAllBusan() {
        String baseYm = RelatedPlaceBaseYm.resolve(LocalDate.now());
        List<CollectedItem> all = new ArrayList<>();
        for (String signguCd : BusanDistrictCode.allCodes()) {
            List<Item> items = fetchDistrictAllPages(baseYm, signguCd);
            if (items.isEmpty()) {
                // 이번 달 데이터가 아직 없으면 전월로 1회 재시도
                items = fetchDistrictAllPages(RelatedPlaceBaseYm.previousMonthOf(baseYm), signguCd);
            }
            for (Item item : items) {
                all.add(new CollectedItem(signguCd, item));
            }
            log.info("연관관광지 수집: 구={}, {}건", signguCd, items.size());
        }
        log.info("연관관광지 전체 수집 완료: 총 {}건", all.size());
        return all;
    }

    private List<Item> fetchDistrictAllPages(String baseYm, String signguCd) {
        List<Item> all = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            List<Item> items = fetchPage(baseYm, signguCd, pageNo);
            if (items.isEmpty()) break;
            all.addAll(items);
            if (items.size() < numOfRows) break;
            pageNo++;
        }
        return all;
    }

    private List<Item> fetchPage(String baseYm, String signguCd, int pageNo) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/areaBasedList1")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", numOfRows)
                .queryParam("pageNo", pageNo)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "BusanTimingDataCollector")
                .queryParam("_type", "json")
                .queryParam("baseYm", baseYm)
                .queryParam("areaCd", BUSAN_AREA_CD)
                .queryParam("signguCd", signguCd)
                .build(true)
                .toUri();
        try {
            RelatedPlaceResponse response = restTemplate.getForObject(uri, RelatedPlaceResponse.class);
            if (response == null || response.getResponse() == null) return Collections.emptyList();
            var header = response.getResponse().getHeader();
            if (header == null || !"0000".equals(header.getResultCode())) {
                log.warn("areaBasedList1 에러: 구={}, {} - {}", signguCd,
                        header != null ? header.getResultCode() : "null",
                        header != null ? header.getResultMsg() : "null");
                return Collections.emptyList();
            }
            var body = response.getResponse().getBody();
            if (body == null || body.getItems() == null || body.getItems() instanceof String) {
                return Collections.emptyList();
            }
            Items items = objectMapper.convertValue(body.getItems(), Items.class);
            return items.getItem() == null ? Collections.emptyList() : items.getItem();
        } catch (Exception e) {
            log.error("areaBasedList1 호출 실패: 구={}, pageNo={}, {}", signguCd, pageNo, e.getMessage());
            return Collections.emptyList();
        }
    }
}
```

- [ ] **Step 2: `BusanDistrictCode.allCodes()` 추가** — 16개 구 코드를 반환하는 메서드가 필요하다. `BusanDistrictCode`에 없으면 추가.

Modify `src/main/java/com/busantiming/sync/BusanDistrictCode.java`:
```java
    public static java.util.Set<String> allCodes() {
        return CODE_TO_NAME.keySet();
    }
```

- [ ] **Step 3: 컴파일 확인** — Run: `./gradlew compileJava` → BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/busantiming/service/RelatedPlaceApiService.java src/main/java/com/busantiming/sync/BusanDistrictCode.java
git commit -m "feat: 연관관광지 API 호출 서비스(16개 구 순회) 추가"
```

---

## Task 6: 원본 수집 배치 job

**Files:**
- Create: `src/main/java/com/busantiming/batch/RelatedPlaceBatchConfig.java`

`FestivalInfoBatchConfig` 미러. 수집 결과를 `related_place` 원본 테이블에 통째 교체. 구별 baseSignguCd는 원본 `signguCd` 컬럼에 저장(응답 자체의 signguCd와 동일하지만, 응답에 signguCd가 비는 경우 대비해 조회 구코드를 신뢰값으로 사용).

- [ ] **Step 1: 작성**

```java
package com.busantiming.batch;

import com.busantiming.domain.RelatedPlace;
import com.busantiming.domain.RelatedPlaceRepository;
import com.busantiming.service.RelatedPlaceApiService;
import com.busantiming.service.RelatedPlaceApiService.CollectedItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Configuration
public class RelatedPlaceBatchConfig {

    private final RelatedPlaceApiService apiService;
    private final RelatedPlaceRepository repository;

    public RelatedPlaceBatchConfig(RelatedPlaceApiService apiService, RelatedPlaceRepository repository) {
        this.apiService = apiService;
        this.repository = repository;
    }

    @Bean
    public Job relatedPlaceJob(JobRepository jobRepository, Step relatedPlaceStep) {
        return new JobBuilder("relatedPlaceJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(relatedPlaceStep)
                .listener(new TourismJobListener())
                .build();
    }

    @Bean
    public Step relatedPlaceStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("relatedPlaceStep", jobRepository)
                .tasklet(relatedPlaceTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet relatedPlaceTasklet() {
        return (contribution, chunkContext) -> {
            log.info("areaBasedList1 API에서 부산 연관관광지를 수집합니다...");
            List<CollectedItem> collected = apiService.fetchAllBusan();

            if (collected.isEmpty()) {
                throw new RuntimeException("연관관광지 API에서 데이터를 수집하지 못했습니다. 기존 데이터를 유지합니다.");
            }

            LocalDateTime now = LocalDateTime.now();
            List<RelatedPlace> rows = collected.stream()
                    .map(c -> RelatedPlace.builder()
                            .baseYm(c.item().getBaseYm())
                            .tAtsCd(c.item().getTAtsCd())
                            .tAtsNm(c.item().getTAtsNm())
                            .areaCd(c.item().getAreaCd())
                            .signguCd(c.baseSignguCd())
                            .signguNm(c.item().getSignguNm())
                            .rlteTatsCd(c.item().getRlteTatsCd())
                            .rlteTatsNm(c.item().getRlteTatsNm())
                            .rlteRegnCd(c.item().getRlteRegnCd())
                            .rlteSignguCd(c.item().getRlteSignguCd())
                            .rlteSignguNm(c.item().getRlteSignguNm())
                            .rlteCtgryLclsNm(c.item().getRlteCtgryLclsNm())
                            .rlteCtgryMclsNm(c.item().getRlteCtgryMclsNm())
                            .rlteCtgrySclsNm(c.item().getRlteCtgrySclsNm())
                            .rlteRank(c.item().getRlteRank())
                            .collectedAt(now)
                            .build())
                    .toList();

            log.info("수집 완료: {}건. 기존 데이터 삭제 후 새 데이터를 저장합니다.", rows.size());
            repository.deleteAllInBatch();
            repository.saveAll(rows);
            log.info("연관관광지 원본 교체 완료: {}건 저장", rows.size());
            return RepeatStatus.FINISHED;
        };
    }
}
```

- [ ] **Step 2: 컴파일 확인** — Run: `./gradlew compileJava` → BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/busantiming/batch/RelatedPlaceBatchConfig.java
git commit -m "feat: 연관관광지 원본 수집 배치 job 추가"
```

---

## Task 7: 서빙 테이블 동기화 step (원본 → places 매칭 → related_places)

**Files:**
- Modify: `src/main/java/com/busantiming/batch/ApiSyncBatchConfig.java`

`ApiSyncBatchConfig`에 `RelatedPlaceRepository`를 주입하고 `syncRelatedPlacesStep`을 추가한다. 원본 `related_place`를 읽어 base/related 양쪽을 `PlaceIdResolver`로 `places`에 매칭, 둘 다 매칭된 페어만 스냅샷 교체(DELETE ALL → batch INSERT ... ON CONFLICT). 축제 sync(`syncFestivalsTasklet`)의 jdbcTemplate 패턴을 그대로 따른다.

- [ ] **Step 1: 생성자/필드에 `RelatedPlaceRepository` 추가**

`import com.busantiming.domain.RelatedPlace;` / `import com.busantiming.domain.RelatedPlaceRepository;` 추가. 필드 `private final RelatedPlaceRepository relatedPlaceRepository;` 추가하고 생성자 파라미터·대입 추가.

- [ ] **Step 2: `apiSyncJob` 체인에 step 연결**

```java
    @Bean
    public Job apiSyncJob(JobRepository jobRepository, Step syncPlacesStep,
                          Step syncCongestionForecastsStep, Step syncFestivalsStep,
                          Step syncRelatedPlacesStep) {
        return new JobBuilder("apiSyncJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(syncPlacesStep)
                .next(syncCongestionForecastsStep)
                .next(syncFestivalsStep)
                .next(syncRelatedPlacesStep)
                .listener(new TourismJobListener())
                .build();
    }
```

- [ ] **Step 3: step + tasklet 추가**

```java
    @Bean
    public Step syncRelatedPlacesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("syncRelatedPlacesStep", jobRepository)
                .tasklet(syncRelatedPlacesTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet syncRelatedPlacesTasklet() {
        return (contribution, chunkContext) -> {
            log.info("public.related_place → busan_timing_api.related_places 동기화 시작");

            List<RelatedPlace> rows = relatedPlaceRepository.findAll();
            if (rows.isEmpty()) {
                log.warn("related_place 데이터가 없습니다. 연관관광지 동기화를 건너뜁니다.");
                return RepeatStatus.FINISHED;
            }

            PlaceIdResolver resolver = new PlaceIdResolver(syncPlaceRepository.findAll());

            // (place_id, related_place_id) 중복 제거를 위해 Set 사용
            record Pair(long placeId, long relatedPlaceId, int rank) {}
            Map<String, Object[]> byKey = new LinkedHashMap<>();
            int unmatched = 0, selfRef = 0;

            for (RelatedPlace r : rows) {
                Optional<Long> baseId = resolver.resolve(r.getTAtsNm(), r.getSignguCd());
                Optional<Long> relatedId = resolver.resolve(r.getRlteTatsNm(), r.getRlteSignguCd());
                if (baseId.isEmpty() || relatedId.isEmpty()) { unmatched++; continue; }
                if (baseId.get().equals(relatedId.get())) { selfRef++; continue; }
                int rank = parseRank(r.getRlteRank());
                String key = baseId.get() + ":" + relatedId.get();
                byKey.putIfAbsent(key, new Object[]{baseId.get(), relatedId.get(), rank});
            }

            jdbcTemplate.update("DELETE FROM busan_timing_api.related_places");

            String sql = """
                    INSERT INTO busan_timing_api.related_places
                        (place_id, related_place_id, rlte_rank, created_at, updated_at)
                    VALUES (?, ?, ?, now(), now())
                    ON CONFLICT (place_id, related_place_id)
                    DO UPDATE SET rlte_rank = EXCLUDED.rlte_rank, updated_at = now()
                    """;
            List<Object[]> params = new ArrayList<>(byKey.values());
            for (int i = 0; i < params.size(); i += BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, params.subList(i, Math.min(i + BATCH_SIZE, params.size())));
            }

            log.info("related_places 동기화 완료: upsert={}건, 매칭실패={}건, 자기참조={}건",
                    params.size(), unmatched, selfRef);
            return RepeatStatus.FINISHED;
        };
    }

    private static int parseRank(String rank) {
        try {
            return rank == null ? 0 : Integer.parseInt(rank.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
```

- [ ] **Step 4: 컴파일 확인** — Run: `./gradlew compileJava` → BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/busantiming/batch/ApiSyncBatchConfig.java
git commit -m "feat: related_place → related_places 서빙 동기화 step 추가"
```

---

## Task 8: 수동 실행 엔드포인트

**Files:**
- Modify: `src/main/java/com/busantiming/batch/BatchRunController.java`

원본 수집 job(`relatedPlaceJob`)을 수동 트리거하는 엔드포인트 추가. 서빙 동기화는 기존 `apiSyncJob`에 이미 포함(Task 7)되므로 별도 엔드포인트 불필요.

- [ ] **Step 1: `relatedPlaceJob` 주입 + 엔드포인트 추가**

생성자에 `@Qualifier("relatedPlaceJob") Job relatedPlaceJob` 추가하고 필드 저장. 엔드포인트:
```java
    @PostMapping("/run/related-place")
    public ResponseEntity<Map<String, String>> runRelatedPlaceBatch() {
        return runJob(relatedPlaceJob, "연관관광지 수집 배치");
    }
```

- [ ] **Step 2: 컴파일 확인** — Run: `./gradlew compileJava` → BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/busantiming/batch/BatchRunController.java
git commit -m "feat: 연관관광지 수집 배치 수동 실행 엔드포인트 추가"
```

---

## Task 9: 통합 검증 (수동)

- [ ] **Step 1: 전체 빌드/테스트** — Run: `./gradlew build` → BUILD SUCCESSFUL
- [ ] **Step 2: 앱 기동 후 원본 수집 실행** — `POST /api/batch/run/related-place` → 로그에서 "연관관광지 원본 교체 완료: N건" 확인, `related_place` 테이블 row 확인.
- [ ] **Step 3: 서빙 동기화 실행** — `POST /api/batch/run/api-sync` → 로그에서 "related_places 동기화 완료: upsert=N건, 매칭실패=M건" 확인.
- [ ] **Step 4: 서빙 데이터 확인** — 예: 특정 관광지의 같은 구 연관 추천 쿼리
  ```sql
  SELECT rp.related_place_id, p2.name, rp.rlte_rank
  FROM busan_timing_api.related_places rp
  JOIN busan_timing_api.places p1 ON p1.id = rp.place_id
  JOIN busan_timing_api.places p2 ON p2.id = rp.related_place_id
  WHERE p1.name = '해운대해수욕장'
    AND p2.district_code = p1.district_code   -- 같은 구
  ORDER BY random() LIMIT 5;
  ```
  → 같은 구 연관관광지가 나오는지 확인.

---

## 주의 / 리스크

- **호출량**: 16개 구 × 페이지 수 ≈ 200~300 호출. data.go.kr 일 1,000건 한도를 overview/축제 수집과 공유하므로 같은 날 겹치면 합산 주의.
- **매칭 손실**: 연관관광지가 부산 밖이거나(`rlteRegnCd != 26`) `places`에 없으면(음식점 등) 서빙 테이블에서 제외됨 — 정상 동작. 손실률은 로그의 "매칭실패" 건수로 모니터링.
- **서빙 스키마 의존**: `related_places`의 컬럼/유니크 제약이 Task 7의 INSERT와 일치해야 함. 다르면 배치 실패 → Flyway 마이그레이션과 대조.
- **`base_ym`은 서빙 테이블에 없음**: 최신 스냅샷만 유지하기로 결정. 추적은 원본 `related_place.base_ym`으로.
