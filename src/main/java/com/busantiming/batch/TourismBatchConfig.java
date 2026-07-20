package com.busantiming.batch;

import com.busantiming.domain.TourismPrediction;
import com.busantiming.domain.TourismPredictionRepository;
import com.busantiming.dto.TourismPredictionResponse.Item;
import com.busantiming.service.TourismApiService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class TourismBatchConfig {

    private static final int BATCH_SIZE = 500;

    private final TourismApiService tourismApiService;
    private final TourismPredictionRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public TourismBatchConfig(TourismApiService tourismApiService,
                              TourismPredictionRepository repository,
                              JdbcTemplate jdbcTemplate) {
        this.tourismApiService = tourismApiService;
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    public Job tourismPredictionJob(JobRepository jobRepository, Step tourismPredictionStep) {
        return new JobBuilder("tourismPredictionJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(tourismPredictionStep)
                .listener(new TourismJobListener())
                .build();
    }

    @Bean
    public Step tourismPredictionStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("tourismPredictionStep", jobRepository)
                .tasklet(tourismTasklet(), transactionManager)
                .build();
    }

    private static final List<Map.Entry<String, String>> BUSAN_DISTRICTS = List.of(
            Map.entry("26110", "중구"),
            Map.entry("26140", "서구"),
            Map.entry("26170", "동구"),
            Map.entry("26200", "영도구"),
            Map.entry("26230", "부산진구"),
            Map.entry("26260", "동래구"),
            Map.entry("26290", "남구"),
            Map.entry("26320", "북구"),
            Map.entry("26350", "해운대구"),
            Map.entry("26380", "사하구"),
            Map.entry("26410", "금정구"),
            Map.entry("26440", "강서구"),
            Map.entry("26470", "연제구"),
            Map.entry("26500", "수영구"),
            Map.entry("26530", "사상구"),
            Map.entry("26710", "기장군")
    );

    @Bean
    public Tasklet tourismTasklet() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        return (contribution, chunkContext) -> {
            log.info("API에서 부산 전체 16개 구군 관광지 집중률 데이터를 수집합니다...");

            List<Item> allItems = new ArrayList<>();
            for (Map.Entry<String, String> district : BUSAN_DISTRICTS) {
                String signguCd = district.getKey();
                String signguNm = district.getValue();
                log.info("수집 중: {} ({})", signguNm, signguCd);

                List<Item> items = tourismApiService.fetchAllPredictions("26", signguCd);
                allItems.addAll(items);
                log.info("{} 수집: {}건", signguNm, items.size());
            }

            if (allItems.isEmpty()) {
                throw new RuntimeException("API에서 데이터를 수집하지 못했습니다. 기존 데이터를 유지합니다.");
            }

            List<TourismPrediction> predictions = allItems.stream()
                    .map(item -> TourismPrediction.builder()
                            .baseYmd(LocalDate.parse(item.getBaseYmd(), formatter))
                            .areaCd(item.getAreaCd())
                            .areaNm(item.getAreaNm())
                            .signguCd(item.getSignguCd())
                            .signguNm(item.getSignguNm())
                            .tourAttractionName(item.getTAtsNm())
                            .cnctrRate(parseDouble(item.getCnctrRate()))
                            .collectedAt(LocalDateTime.now())
                            .build())
                    .toList();

            log.info("전체 수집 완료: {}건. 기존 데이터 삭제 후 새 데이터를 저장합니다.", predictions.size());
            repository.deleteAllInBatch();
            bulkInsert(predictions);
            log.info("데이터 교체 완료: {}건 저장", predictions.size());

            return RepeatStatus.FINISHED;
        };
    }

    /**
     * IDENTITY 전략 엔티티는 saveAll 시 행 단위 INSERT 왕복이 발생해 대량 저장이 매우 느리다.
     * (원격 DB 기준 수천 건이면 십수 분 소요 → 배치 타임아웃 유발)
     * jdbcTemplate.batchUpdate로 벌크 인서트하여 왕복 횟수를 줄인다.
     */
    private void bulkInsert(List<TourismPrediction> predictions) {
        String sql = """
                INSERT INTO public.tourism_concentration
                    (base_ymd, area_cd, area_nm, signgu_cd, signgu_nm, tour_attraction_name, cnctr_rate, collected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                """;

        List<Object[]> params = predictions.stream()
                .map(p -> new Object[]{
                        Date.valueOf(p.getBaseYmd()),
                        p.getAreaCd(),
                        p.getAreaNm(),
                        p.getSignguCd(),
                        p.getSignguNm(),
                        p.getTourAttractionName(),
                        p.getCnctrRate()
                })
                .toList();

        for (int i = 0; i < params.size(); i += BATCH_SIZE) {
            List<Object[]> batch = params.subList(i, Math.min(i + BATCH_SIZE, params.size()));
            jdbcTemplate.batchUpdate(sql, batch);
        }
    }

    private Double parseDouble(String value) {
        try {
            return value != null ? Double.parseDouble(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
