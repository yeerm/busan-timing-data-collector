package com.busantiming.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@EnableScheduling
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job tourismPredictionJob;
    private final Job tourismInfoJob;
    private final Job apiSyncJob;

    public BatchScheduler(JobLauncher jobLauncher,
                          @Qualifier("tourismPredictionJob") Job tourismPredictionJob,
                          @Qualifier("tourismInfoJob") Job tourismInfoJob,
                          @Qualifier("apiSyncJob") Job apiSyncJob) {
        this.jobLauncher = jobLauncher;
        this.tourismPredictionJob = tourismPredictionJob;
        this.tourismInfoJob = tourismInfoJob;
        this.apiSyncJob = apiSyncJob;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void runTourismPredictionJob() {
        log.info("스케줄러에 의한 집중률 예측 배치 실행: {}", LocalDateTime.now());
        runJob(tourismPredictionJob, "tourismPredictionJob");
    }

    @Scheduled(cron = "0 30 0 * * MON")
    public void runTourismInfoJob() {
        log.info("스케줄러에 의한 관광정보 배치 실행: {}", LocalDateTime.now());
        runJob(tourismInfoJob, "tourismInfoJob");
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void runApiSyncJob() {
        log.info("스케줄러에 의한 API 동기화 배치 실행: {}", LocalDateTime.now());
        runJob(apiSyncJob, "apiSyncJob");
    }

    private void runJob(Job job, String jobName) {
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        try {
            jobLauncher.run(job, params);
        } catch (Exception e) {
            log.error("{} 배치 실행 실패: {}", jobName, e.getMessage(), e);
        }
    }
}
