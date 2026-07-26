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

    /** 각 Item에 어느 구에서 조회했는지(구코드)를 실어 나르기 위한 캐리어. */
    public record CollectedItem(String baseSignguCd, Item item) {
    }

    public RelatedPlaceApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** 부산 16개 구 전체를 수집한다. */
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
            if (response == null || response.getResponse() == null) {
                return Collections.emptyList();
            }
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
