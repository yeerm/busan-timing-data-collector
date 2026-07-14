package com.busantiming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class BusanTimingApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(BusanTimingApplication.class, args);

        // 배치 모드(--spring.batch.job.enabled=true)로 실행된 경우, Job 완료 후 JVM을 종료한다.
        // 웹 서비스 배포 시에는 이 플래그가 false(기본값)이므로 종료하지 않고 계속 떠 있는다.
        boolean batchMode = context.getEnvironment()
                .getProperty("spring.batch.job.enabled", Boolean.class, false);
        if (batchMode) {
            int exitCode = SpringApplication.exit(context);
            System.exit(exitCode);
        }
    }
}
