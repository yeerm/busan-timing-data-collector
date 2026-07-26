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
