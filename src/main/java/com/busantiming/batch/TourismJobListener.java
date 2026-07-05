package com.busantiming.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.time.Duration;

@Slf4j
public class TourismJobListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("========================================");
        log.info("관광지 집중률 수집 배치 시작");
        log.info("========================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        Duration duration = Duration.between(
                jobExecution.getStartTime(), jobExecution.getEndTime());

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("========================================");
            log.info("배치 완료 - 소요시간: {}초", duration.getSeconds());
            log.info("========================================");
        } else {
            log.error("========================================");
            log.error("배치 실패 - 상태: {}", jobExecution.getStatus());
            jobExecution.getAllFailureExceptions()
                    .forEach(e -> log.error("실패 원인: {}", e.getMessage()));
            log.error("========================================");
        }
    }
}
