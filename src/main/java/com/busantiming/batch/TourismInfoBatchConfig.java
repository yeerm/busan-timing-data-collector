package com.busantiming.batch;

import com.busantiming.domain.TourismInfo;
import com.busantiming.domain.TourismInfoRepository;
import com.busantiming.dto.TourismInfoResponse.Item;
import com.busantiming.service.TourismInfoApiService;
import com.busantiming.sync.BusanDistrictCode;
import com.busantiming.sync.SyncDataTransformer;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class TourismInfoBatchConfig {

    private final TourismInfoApiService tourismInfoApiService;
    private final TourismInfoRepository repository;

    public TourismInfoBatchConfig(TourismInfoApiService tourismInfoApiService, TourismInfoRepository repository) {
        this.tourismInfoApiService = tourismInfoApiService;
        this.repository = repository;
    }

    @Bean
    public Job tourismInfoJob(JobRepository jobRepository, Step tourismInfoStep) {
        return new JobBuilder("tourismInfoJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(tourismInfoStep)
                .listener(new TourismJobListener())
                .build();
    }

    @Bean
    public Step tourismInfoStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("tourismInfoStep", jobRepository)
                .tasklet(tourismInfoTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet tourismInfoTasklet() {
        return (contribution, chunkContext) -> {
            log.info("KorService2 API에서 부산 관광정보를 수집합니다...");
            List<Item> items = tourismInfoApiService.fetchAllTourismInfo();

            if (items.isEmpty()) {
                throw new RuntimeException("KorService2 API에서 데이터를 수집하지 못했습니다. 기존 데이터를 유지합니다.");
            }

            // 기존 overview 캐시: content_id -> 기존 TourismInfo (modified_time 비교로 재사용 여부 판단)
            Map<String, TourismInfo> existingByContentId = repository.findAll().stream()
                    .filter(t -> t.getContentId() != null)
                    .collect(Collectors.toMap(TourismInfo::getContentId, Function.identity(), (a, b) -> a));

            LocalDateTime now = LocalDateTime.now();
            int[] counters = new int[2]; // [0]=캐시재사용, [1]=신규조회
            List<TourismInfo> tourismInfoList = items.stream()
                    .map(item -> TourismInfo.builder()
                            .contentId(item.getContentid())
                            .contentTypeId(item.getContenttypeid())
                            .title(item.getTitle())
                            .addr1(item.getAddr1())
                            .addr2(item.getAddr2())
                            .zipcode(item.getZipcode())
                            .tel(item.getTel())
                            .firstImage(item.getFirstimage())
                            .firstImage2(item.getFirstimage2())
                            .mapx(item.getMapx())
                            .mapy(item.getMapy())
                            .lDongRegnCd(item.getLDongRegnCd() != null ? item.getLDongRegnCd() : "26")
                            .lDongSignguCd(resolveDistrictCode(item))
                            .createdTime(item.getCreatedtime())
                            .modifiedTime(item.getModifiedtime())
                            .overview(resolveOverview(item, existingByContentId, counters))
                            .collectedAt(now)
                            .build())
                    .toList();

            log.info("overview 처리 완료: 캐시 재사용 {}건, detailCommon2 신규 조회 {}건", counters[0], counters[1]);
            log.info("수집 완료: {}건. 기존 데이터 삭제 후 새 데이터를 저장합니다.", tourismInfoList.size());
            repository.deleteAllInBatch();
            repository.saveAll(tourismInfoList);
            log.info("관광정보 데이터 교체 완료: {}건 저장", tourismInfoList.size());

            return RepeatStatus.FINISHED;
        };
    }

    /**
     * overview 결정: 기존에 같은 content_id가 있고 modified_time이 동일하면 캐시된 값을 재사용하고,
     * 신규이거나 modified_time이 바뀐 경우에만 detailCommon2를 호출한다.
     * (설명이 아예 없는 콘텐츠도 modified_time이 같으면 재조회하지 않는다.)
     */
    private String resolveOverview(Item item, Map<String, TourismInfo> existingByContentId, int[] counters) {
        TourismInfo existing = existingByContentId.get(item.getContentid());
        if (existing != null && java.util.Objects.equals(existing.getModifiedTime(), item.getModifiedtime())) {
            counters[0]++;
            return existing.getOverview();
        }
        counters[1]++;
        return tourismInfoApiService.fetchOverview(item.getContentid());
    }

    private String resolveDistrictCode(Item item) {
        if (item.getLDongSignguCd() != null && !item.getLDongSignguCd().isBlank()) {
            return item.getLDongSignguCd();
        }
        String districtName = SyncDataTransformer.extractDistrictName(item.getAddr1());
        String code = BusanDistrictCode.getCodeByName(districtName);
        return code != null ? code : null;
    }
}
