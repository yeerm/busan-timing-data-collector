package com.busantiming.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/batch")
public class BatchRunController {

    private final JobLauncher jobLauncher;
    private final Job tourismPredictionJob;
    private final Job tourismInfoJob;
    private final Job apiSyncJob;

    public BatchRunController(JobLauncher jobLauncher,
                              @Qualifier("tourismPredictionJob") Job tourismPredictionJob,
                              @Qualifier("tourismInfoJob") Job tourismInfoJob,
                              @Qualifier("apiSyncJob") Job apiSyncJob) {
        this.jobLauncher = jobLauncher;
        this.tourismPredictionJob = tourismPredictionJob;
        this.tourismInfoJob = tourismInfoJob;
        this.apiSyncJob = apiSyncJob;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> runPredictionBatch() {
        return runJob(tourismPredictionJob, "집중률 예측 배치");
    }

    @PostMapping("/run/tourism-info")
    public ResponseEntity<Map<String, String>> runTourismInfoBatch() {
        return runJob(tourismInfoJob, "관광정보 배치");
    }

    @PostMapping("/run/api-sync")
    public ResponseEntity<Map<String, String>> runApiSyncBatch() {
        return runJob(apiSyncJob, "API 동기화 배치");
    }

    private ResponseEntity<Map<String, String>> runJob(Job job, String jobName) {
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        try {
            jobLauncher.run(job, params);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", jobName + "가 실행되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "FAIL", "message", e.getMessage()));
        }
    }
}
