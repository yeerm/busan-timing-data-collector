package com.busantiming.service;

import com.busantiming.dto.TourismDetailResponse;
import com.busantiming.dto.TourismInfoResponse;
import com.busantiming.dto.TourismInfoResponse.Item;
import com.busantiming.dto.TourismInfoResponse.Items;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class TourismInfoApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${api.data-go-kr.service-key}")
    private String serviceKey;

    @Value("${api.data-go-kr.num-of-rows}")
    private int numOfRows;

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";

    public TourismInfoApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Item> fetchTourismInfo(int pageNo) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/areaBasedList2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", numOfRows)
                .queryParam("pageNo", pageNo)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "BusanTimingDataCollector")
                .queryParam("_type", "json")
                .queryParam("arrange", "C")
                .queryParam("lDongRegnCd", "26")
                .build(true)
                .toUri();

        log.debug("KorService2 API 호출: {}", uri);

        try {
            TourismInfoResponse response = restTemplate.getForObject(uri, TourismInfoResponse.class);

            if (response == null || response.getResponse() == null) {
                log.warn("API 응답이 null입니다.");
                return Collections.emptyList();
            }

            var header = response.getResponse().getHeader();
            if (!"0000".equals(header.getResultCode())) {
                log.error("API 에러: {} - {}", header.getResultCode(), header.getResultMsg());
                return Collections.emptyList();
            }

            var body = response.getResponse().getBody();
            if (body == null || body.getItems() == null || body.getItems() instanceof String) {
                log.info("조회된 데이터가 없습니다. pageNo={}", pageNo);
                return Collections.emptyList();
            }

            Items items = objectMapper.convertValue(body.getItems(), Items.class);
            if (items.getItem() == null || items.getItem().isEmpty()) {
                log.info("조회된 데이터가 없습니다. pageNo={}", pageNo);
                return Collections.emptyList();
            }

            log.info("KorService2 API 응답: pageNo={}, 조회건수={}, 전체건수={}",
                    pageNo, items.getItem().size(), body.getTotalCount());
            return items.getItem();

        } catch (Exception e) {
            log.error("KorService2 API 호출 실패: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * detailCommon2(공통정보 조회)로 특정 콘텐츠의 overview(설명)를 가져온다.
     * 설명이 없거나 호출 실패 시 null을 반환한다.
     */
    public String fetchOverview(String contentId) {
        if (contentId == null || contentId.isBlank()) {
            return null;
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/detailCommon2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "BusanTimingDataCollector")
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .build(true)
                .toUri();

        try {
            TourismDetailResponse response = restTemplate.getForObject(uri, TourismDetailResponse.class);

            if (response == null || response.getResponse() == null) {
                return null;
            }

            var header = response.getResponse().getHeader();
            if (header == null || !"0000".equals(header.getResultCode())) {
                log.warn("detailCommon2 에러: contentId={}, code={}", contentId,
                        header != null ? header.getResultCode() : "null");
                return null;
            }

            var body = response.getResponse().getBody();
            if (body == null || body.getItems() == null || body.getItems() instanceof String) {
                return null;
            }

            TourismDetailResponse.Items items =
                    objectMapper.convertValue(body.getItems(), TourismDetailResponse.Items.class);
            if (items.getItem() == null || items.getItem().isEmpty()) {
                return null;
            }

            String overview = items.getItem().get(0).getOverview();
            if (overview == null || overview.isBlank()) {
                return null;
            }
            return overview.trim();

        } catch (Exception e) {
            log.error("detailCommon2 호출 실패: contentId={}, {}", contentId, e.getMessage());
            return null;
        }
    }

    public List<Item> fetchAllTourismInfo() {
        List<Item> allItems = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            List<Item> items = fetchTourismInfo(pageNo);
            if (items.isEmpty()) {
                break;
            }
            allItems.addAll(items);

            if (items.size() < numOfRows) {
                break;
            }
            pageNo++;
        }

        log.info("KorService2 수집 완료: 총 {}건", allItems.size());
        return allItems;
    }
}
