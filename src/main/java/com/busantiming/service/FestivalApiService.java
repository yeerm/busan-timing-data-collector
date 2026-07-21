package com.busantiming.service;

import com.busantiming.dto.FestivalResponse;
import com.busantiming.dto.FestivalResponse.Item;
import com.busantiming.dto.FestivalResponse.Items;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * KorService2 searchFestival2(행사정보조회)로 부산 축제 정보를 수집한다.
 */
@Slf4j
@Service
public class FestivalApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${api.data-go-kr.service-key}")
    private String serviceKey;

    @Value("${api.data-go-kr.num-of-rows}")
    private int numOfRows;

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";
    // 부산 시도 코드(lDong 체계). searchFestival2는 lDongRegnCd로 지역 필터가 정상 동작함(areaCode=6은 거의 안 잡힘).
    private static final String BUSAN_L_DONG_REGN_CD = "26";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    public FestivalApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Item> fetchFestivals(String eventStartDate, int pageNo) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/searchFestival2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", numOfRows)
                .queryParam("pageNo", pageNo)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "BusanTimingDataCollector")
                .queryParam("_type", "json")
                .queryParam("arrange", "A")
                .queryParam("eventStartDate", eventStartDate)
                .queryParam("lDongRegnCd", BUSAN_L_DONG_REGN_CD)
                .build(true)
                .toUri();

        try {
            FestivalResponse response = restTemplate.getForObject(uri, FestivalResponse.class);

            if (response == null || response.getResponse() == null) {
                log.warn("searchFestival2 응답이 null입니다.");
                return Collections.emptyList();
            }

            var header = response.getResponse().getHeader();
            if (header == null || !"0000".equals(header.getResultCode())) {
                log.error("searchFestival2 에러: {} - {}",
                        header != null ? header.getResultCode() : "null",
                        header != null ? header.getResultMsg() : "null");
                return Collections.emptyList();
            }

            var body = response.getResponse().getBody();
            if (body == null || body.getItems() == null || body.getItems() instanceof String) {
                log.info("조회된 축제가 없습니다. pageNo={}", pageNo);
                return Collections.emptyList();
            }

            Items items = objectMapper.convertValue(body.getItems(), Items.class);
            if (items.getItem() == null || items.getItem().isEmpty()) {
                return Collections.emptyList();
            }

            log.info("searchFestival2 응답: pageNo={}, 조회건수={}, 전체건수={}",
                    pageNo, items.getItem().size(), body.getTotalCount());
            return items.getItem();

        } catch (Exception e) {
            log.error("searchFestival2 호출 실패: pageNo={}, {}", pageNo, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 올해 1월 1일 이후 시작하는 부산 축제를 전체 페이지 수집한다.
     */
    public List<Item> fetchAllFestivals() {
        String eventStartDate = LocalDate.now().withDayOfYear(1).format(YYYYMMDD);
        List<Item> allItems = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            List<Item> items = fetchFestivals(eventStartDate, pageNo);
            if (items.isEmpty()) {
                break;
            }
            allItems.addAll(items);
            if (items.size() < numOfRows) {
                break;
            }
            pageNo++;
        }

        log.info("searchFestival2 수집 완료: eventStartDate={}, 총 {}건", eventStartDate, allItems.size());
        return allItems;
    }
}
