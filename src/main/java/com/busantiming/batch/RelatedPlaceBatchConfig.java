package com.busantiming.batch;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class RelatedPlaceBatchConfig {

    private final RelatedPlaceApiService apiService;
    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_SIZE = 1000;

    public RelatedPlaceBatchConfig(RelatedPlaceApiService apiService, JdbcTemplate jdbcTemplate) {
        this.apiService = apiService;
        this.jdbcTemplate = jdbcTemplate;
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

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            List<Object[]> params = new ArrayList<>(collected.size());
            for (CollectedItem c : collected) {
                var i = c.item();
                params.add(new Object[]{
                        i.getBaseYm(), i.getTAtsCd(), i.getTAtsNm(), i.getAreaCd(),
                        c.baseSignguCd(), i.getSignguNm(), i.getRlteTatsCd(), i.getRlteTatsNm(),
                        i.getRlteRegnCd(), i.getRlteSignguCd(), i.getRlteSignguNm(),
                        i.getRlteCtgryLclsNm(), i.getRlteCtgryMclsNm(), i.getRlteCtgrySclsNm(),
                        i.getRlteRank(), now});
            }

            log.info("수집 완료: {}건. 기존 데이터 삭제 후 배치 저장합니다.", params.size());
            jdbcTemplate.update("DELETE FROM related_place");

            String sql = """
                    INSERT INTO related_place
                        (base_ym, t_ats_cd, t_ats_nm, area_cd, signgu_cd, signgu_nm,
                         rlte_tats_cd, rlte_tats_nm, rlte_regn_cd, rlte_signgu_cd, rlte_signgu_nm,
                         rlte_ctgry_lcls_nm, rlte_ctgry_mcls_nm, rlte_ctgry_scls_nm, rlte_rank, collected_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            for (int i = 0; i < params.size(); i += BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, params.subList(i, Math.min(i + BATCH_SIZE, params.size())));
            }
            log.info("연관관광지 원본 교체 완료: {}건 저장", params.size());
            return RepeatStatus.FINISHED;
        };
    }
}
