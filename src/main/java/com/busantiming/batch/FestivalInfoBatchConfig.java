package com.busantiming.batch;

import com.busantiming.domain.FestivalInfo;
import com.busantiming.domain.FestivalInfoRepository;
import com.busantiming.dto.FestivalResponse.Item;
import com.busantiming.service.FestivalApiService;
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
public class FestivalInfoBatchConfig {

    private final FestivalApiService festivalApiService;
    private final FestivalInfoRepository repository;

    public FestivalInfoBatchConfig(FestivalApiService festivalApiService, FestivalInfoRepository repository) {
        this.festivalApiService = festivalApiService;
        this.repository = repository;
    }

    @Bean
    public Job festivalInfoJob(JobRepository jobRepository, Step festivalInfoStep) {
        return new JobBuilder("festivalInfoJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(festivalInfoStep)
                .listener(new TourismJobListener())
                .build();
    }

    @Bean
    public Step festivalInfoStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("festivalInfoStep", jobRepository)
                .tasklet(festivalInfoTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet festivalInfoTasklet() {
        return (contribution, chunkContext) -> {
            log.info("searchFestival2 API에서 부산 축제정보를 수집합니다...");
            List<Item> items = festivalApiService.fetchAllFestivals();

            if (items.isEmpty()) {
                throw new RuntimeException("searchFestival2 API에서 데이터를 수집하지 못했습니다. 기존 데이터를 유지합니다.");
            }

            LocalDateTime now = LocalDateTime.now();
            List<FestivalInfo> festivalList = items.stream()
                    .map(item -> FestivalInfo.builder()
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
                            .eventStartDate(item.getEventstartdate())
                            .eventEndDate(item.getEventenddate())
                            .lDongRegnCd(item.getLDongRegnCd())
                            .lDongSignguCd(item.getLDongSignguCd())
                            .createdTime(item.getCreatedtime())
                            .modifiedTime(item.getModifiedtime())
                            .collectedAt(now)
                            .build())
                    .toList();

            log.info("수집 완료: {}건. 기존 데이터 삭제 후 새 데이터를 저장합니다.", festivalList.size());
            repository.deleteAllInBatch();
            repository.saveAll(festivalList);
            log.info("축제정보 데이터 교체 완료: {}건 저장", festivalList.size());

            return RepeatStatus.FINISHED;
        };
    }
}
