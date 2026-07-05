package com.busantiming.service;

import com.busantiming.dto.TourismPredictionResponse;
import com.busantiming.dto.TourismPredictionResponse.Item;
import com.busantiming.dto.TourismPredictionResponse.Items;
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
import java.util.Map;

@Slf4j
@Service
public class TourismApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${api.data-go-kr.base-url}")
    private String baseUrl;

    @Value("${api.data-go-kr.service-key}")
    private String serviceKey;

    @Value("${api.data-go-kr.num-of-rows}")
    private int numOfRows;

    public TourismApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Item> fetchPredictions(String areaCd, String signguCd, int pageNo) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/tatsCnctrRatedList")
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", numOfRows)
                .queryParam("pageNo", pageNo)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "BusanTimingDataCollector")
                .queryParam("areaCd", areaCd)
                .queryParam("signguCd", signguCd)
                .queryParam("_type", "json")
                .build(true)
                .toUri();

        log.debug("API 호출: {}", uri);

        try {
            TourismPredictionResponse response = restTemplate.getForObject(uri, TourismPredictionResponse.class);

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

            log.info("API 응답: areaCd={}, signguCd={}, pageNo={}, 조회건수={}, 전체건수={}",
                    areaCd, signguCd, pageNo, items.getItem().size(), body.getTotalCount());
            return items.getItem();

        } catch (Exception e) {
            log.error("API 호출 실패: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public List<Item> fetchAllPredictions(String areaCd, String signguCd) {
        List<Item> allItems = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            List<Item> items = fetchPredictions(areaCd, signguCd, pageNo);
            if (items.isEmpty()) {
                break;
            }
            allItems.addAll(items);

            if (items.size() < numOfRows) {
                break;
            }
            pageNo++;
        }

        log.info("수집 완료: areaCd={}, signguCd={}, 총 {}건", areaCd, signguCd, allItems.size());
        return allItems;
    }
}
