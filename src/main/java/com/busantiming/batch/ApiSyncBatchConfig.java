package com.busantiming.batch;

import com.busantiming.domain.FestivalInfo;
import com.busantiming.domain.FestivalInfoRepository;
import com.busantiming.domain.TourismInfo;
import com.busantiming.domain.TourismInfoRepository;
import com.busantiming.domain.TourismPrediction;
import com.busantiming.domain.TourismPredictionRepository;
import com.busantiming.sync.*;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class ApiSyncBatchConfig {

    private final TourismInfoRepository tourismInfoRepository;
    private final TourismPredictionRepository tourismPredictionRepository;
    private final SyncPlaceRepository syncPlaceRepository;
    private final SyncCongestionForecastRepository syncCongestionForecastRepository;
    private final ContentTypeMappingRepository contentTypeMappingRepository;
    private final FestivalInfoRepository festivalInfoRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_SIZE = 500;

    public ApiSyncBatchConfig(TourismInfoRepository tourismInfoRepository,
                              TourismPredictionRepository tourismPredictionRepository,
                              SyncPlaceRepository syncPlaceRepository,
                              SyncCongestionForecastRepository syncCongestionForecastRepository,
                              ContentTypeMappingRepository contentTypeMappingRepository,
                              FestivalInfoRepository festivalInfoRepository,
                              JdbcTemplate jdbcTemplate) {
        this.tourismInfoRepository = tourismInfoRepository;
        this.tourismPredictionRepository = tourismPredictionRepository;
        this.syncPlaceRepository = syncPlaceRepository;
        this.syncCongestionForecastRepository = syncCongestionForecastRepository;
        this.contentTypeMappingRepository = contentTypeMappingRepository;
        this.festivalInfoRepository = festivalInfoRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    public Job apiSyncJob(JobRepository jobRepository, Step syncPlacesStep,
                          Step syncCongestionForecastsStep, Step syncFestivalsStep) {
        return new JobBuilder("apiSyncJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(syncPlacesStep)
                .next(syncCongestionForecastsStep)
                .next(syncFestivalsStep)
                .listener(new TourismJobListener())
                .build();
    }

    @Bean
    public Step syncPlacesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("syncPlacesStep", jobRepository)
                .tasklet(syncPlacesTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Step syncCongestionForecastsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("syncCongestionForecastsStep", jobRepository)
                .tasklet(syncCongestionForecastsTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Step syncFestivalsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("syncFestivalsStep", jobRepository)
                .tasklet(syncFestivalsTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet syncPlacesTasklet() {
        return (contribution, chunkContext) -> {
            log.info("public.tourism_info → busan_timing_api.places 동기화 시작");

            List<TourismInfo> tourismInfoList = tourismInfoRepository.findAll();
            if (tourismInfoList.isEmpty()) {
                throw new RuntimeException("tourism_info 데이터가 없습니다. 동기화를 중단합니다.");
            }

            Set<Integer> validContentTypeIds = contentTypeMappingRepository.findAllContentTypeIds();
            ContentTypeValidator validator = new ContentTypeValidator(validContentTypeIds);
            log.info("유효한 content_type_id 목록: {}", validContentTypeIds);

            Map<String, SyncPlace> existingMap = syncPlaceRepository.findAll().stream()
                    .filter(p -> p.getContentId() != null)
                    .collect(Collectors.toMap(SyncPlace::getContentId, Function.identity(), (a, b) -> a));

            LocalDateTime now = LocalDateTime.now();
            List<SyncPlace> toSave = new ArrayList<>();
            int skippedCount = 0;

            for (TourismInfo info : tourismInfoList) {
                if (info.getContentId() == null) continue;

                Integer contentTypeId = validator.parseAndValidate(info.getContentTypeId());
                if (contentTypeId == null) {
                    skippedCount++;
                    log.warn("[SKIP] contentId={}, contentTypeId={} - 유효하지 않은 content_type_id",
                            info.getContentId(), info.getContentTypeId());
                    continue;
                }

                SyncPlace existing = existingMap.get(info.getContentId());
                if (existing != null) {
                    updatePlace(existing, info, contentTypeId, now);
                    toSave.add(existing);
                } else {
                    toSave.add(createPlace(info, contentTypeId, now));
                }
            }

            syncPlaceRepository.saveAll(toSave);
            log.info("places 동기화 완료: {}건 upsert, {}건 skip (유효하지 않은 content_type_id)", toSave.size(), skippedCount);

            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Tasklet syncCongestionForecastsTasklet() {
        return (contribution, chunkContext) -> {
            log.info("public.tourism_concentration → busan_timing_api.congestion_forecasts 동기화 시작");

            List<TourismInfo> tourismInfoList = tourismInfoRepository.findAll();
            List<TourismPrediction> predictions = tourismPredictionRepository.findAll();

            if (predictions.isEmpty()) {
                log.warn("tourism_concentration 데이터가 없습니다. 혼잡도 동기화를 건너뜁니다.");
                return RepeatStatus.FINISHED;
            }

            PlaceMatchingService matchingService = new PlaceMatchingService(tourismInfoList);

            Map<String, SyncPlace> placeByContentId = syncPlaceRepository.findAll().stream()
                    .filter(p -> p.getContentId() != null)
                    .collect(Collectors.toMap(SyncPlace::getContentId, Function.identity(), (a, b) -> a));

            int unmatchedCount = 0;
            List<String> unmatchedReport = new ArrayList<>();
            List<Object[]> upsertParams = new ArrayList<>();

            Set<String> processedAttractionNames = new HashSet<>();
            for (TourismPrediction prediction : predictions) {
                String attractionName = prediction.getTourAttractionName();

                PlaceMatchResult matchResult = matchingService.match(attractionName, prediction.getSignguNm());

                if (!matchResult.isMatched()) {
                    if (processedAttractionNames.add(attractionName)) {
                        unmatchedCount++;
                        unmatchedReport.add(String.format(
                                "[매칭실패] signgu_cd=%s, signgu_nm=%s, tour_attraction_name=%s, base_ymd=%s, cnctr_rate=%s, 사유=%s",
                                prediction.getSignguCd(), prediction.getSignguNm(),
                                attractionName, prediction.getBaseYmd(),
                                prediction.getCnctrRate(), matchResult.getFailureReason()));
                    }
                    continue;
                }

                TourismInfo matched = matchResult.getTourismInfo();
                SyncPlace place = placeByContentId.get(matched.getContentId());
                if (place == null) continue;

                Integer score = SyncDataTransformer.clampCongestionScore(prediction.getCnctrRate());
                if (score == null) continue;

                upsertParams.add(new Object[]{place.getId(), Date.valueOf(prediction.getBaseYmd()), score});
            }

            String sql = """
                    INSERT INTO busan_timing_api.congestion_forecasts (place_id, forecast_date, congestion_score, created_at, updated_at)
                    VALUES (?, ?, ?, now(), now())
                    ON CONFLICT (place_id, forecast_date)
                    DO UPDATE SET congestion_score = EXCLUDED.congestion_score, updated_at = now()
                    """;

            for (int i = 0; i < upsertParams.size(); i += BATCH_SIZE) {
                List<Object[]> batch = upsertParams.subList(i, Math.min(i + BATCH_SIZE, upsertParams.size()));
                jdbcTemplate.batchUpdate(sql, batch);
            }

            if (!unmatchedReport.isEmpty()) {
                log.warn("혼잡도 매칭 실패 관광지 {}건:", unmatchedCount);
                unmatchedReport.forEach(log::warn);
            }

            syncPlaceRepository.updateMonthlyAverageCongestionScores();

            log.info("congestion_forecasts 동기화 완료: 매칭성공={}건, 매칭실패={}건", upsertParams.size(), unmatchedCount);

            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Tasklet syncFestivalsTasklet() {
        return (contribution, chunkContext) -> {
            log.info("public.festival_info → busan_timing_api.place_festivals 동기화 시작");

            List<FestivalInfo> festivals = festivalInfoRepository.findAll();
            if (festivals.isEmpty()) {
                log.warn("festival_info 데이터가 없습니다. 축제 동기화를 건너뜁니다.");
                return RepeatStatus.FINISHED;
            }

            Map<String, SyncPlace> placeByContentId = syncPlaceRepository.findAll().stream()
                    .filter(p -> p.getContentId() != null)
                    .collect(Collectors.toMap(SyncPlace::getContentId, Function.identity(), (a, b) -> a));

            LocalDate today = LocalDate.now();
            List<Object[]> upsertParams = new ArrayList<>();
            int unmatchedCount = 0;
            int invalidDateCount = 0;

            for (FestivalInfo festival : festivals) {
                if (festival.getContentId() == null) continue;

                SyncPlace place = placeByContentId.get(festival.getContentId());
                if (place == null) {
                    unmatchedCount++;
                    log.warn("[SKIP] contentId={}, {} - places에 매칭되는 관광지 없음",
                            festival.getContentId(), festival.getTitle());
                    continue;
                }

                LocalDate startDate = SyncDataTransformer.parseYyyyMmDd(festival.getEventStartDate());
                LocalDate endDate = SyncDataTransformer.parseYyyyMmDd(festival.getEventEndDate());
                if (startDate == null || endDate == null) {
                    invalidDateCount++;
                    log.warn("[SKIP] contentId={}, {} - 행사 기간 파싱 실패(start={}, end={})",
                            festival.getContentId(), festival.getTitle(),
                            festival.getEventStartDate(), festival.getEventEndDate());
                    continue;
                }

                boolean active = !endDate.isBefore(today);
                String name = festival.getTitle() != null ? festival.getTitle().trim() : place.getName();
                upsertParams.add(new Object[]{place.getId(), name, Date.valueOf(startDate), Date.valueOf(endDate), active});
            }

            String sql = """
                    INSERT INTO busan_timing_api.place_festivals
                        (place_id, name, start_date, end_date, active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, now(), now())
                    ON CONFLICT (place_id)
                    DO UPDATE SET name = EXCLUDED.name,
                                  start_date = EXCLUDED.start_date,
                                  end_date = EXCLUDED.end_date,
                                  active = EXCLUDED.active,
                                  updated_at = now()
                    """;

            for (int i = 0; i < upsertParams.size(); i += BATCH_SIZE) {
                List<Object[]> batch = upsertParams.subList(i, Math.min(i + BATCH_SIZE, upsertParams.size()));
                jdbcTemplate.batchUpdate(sql, batch);
            }

            log.info("place_festivals 동기화 완료: upsert={}건, 매칭실패={}건, 기간오류={}건",
                    upsertParams.size(), unmatchedCount, invalidDateCount);

            return RepeatStatus.FINISHED;
        };
    }

    private SyncPlace createPlace(TourismInfo info, int contentTypeId, LocalDateTime now) {
        String districtName = SyncDataTransformer.extractDistrictName(info.getAddr1());
        return SyncPlace.builder()
                .contentId(info.getContentId())
                .name(info.getTitle() != null ? info.getTitle().trim() : "")
                .districtName(districtName.isEmpty() ? "기타" : districtName)
                .districtCode(info.getLDongSignguCd() != null ? info.getLDongSignguCd() : "00000")
                .contentTypeId(contentTypeId)
                .address(SyncDataTransformer.buildAddress(info.getAddr1(), info.getAddr2()))
                .imageUrl(SyncDataTransformer.resolveImageUrl(info.getFirstImage(), info.getFirstImage2()))
                .lat(SyncDataTransformer.parseCoordinate(info.getMapy()))
                .lng(SyncDataTransformer.parseCoordinate(info.getMapx()))
                .description(SyncDataTransformer.resolveDescription(info.getOverview(), info.getTitle()))
                .last7DaysDetailViewCount(0)
                .monthlyDetailViewCount(0)
                .monthlyAverageCongestionScore(0)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void updatePlace(SyncPlace place, TourismInfo info, int contentTypeId, LocalDateTime now) {
        String districtName = SyncDataTransformer.extractDistrictName(info.getAddr1());
        place.setName(info.getTitle() != null ? info.getTitle().trim() : place.getName());
        place.setDistrictName(districtName.isEmpty() ? place.getDistrictName() : districtName);
        place.setDistrictCode(info.getLDongSignguCd() != null ? info.getLDongSignguCd() : place.getDistrictCode());
        place.setContentTypeId(contentTypeId);
        place.setAddress(SyncDataTransformer.buildAddress(info.getAddr1(), info.getAddr2()));
        place.setImageUrl(SyncDataTransformer.resolveImageUrl(info.getFirstImage(), info.getFirstImage2()));
        place.setLat(SyncDataTransformer.parseCoordinate(info.getMapy()));
        place.setLng(SyncDataTransformer.parseCoordinate(info.getMapx()));
        place.setDescription(SyncDataTransformer.resolveDescription(info.getOverview(), info.getTitle()));
        place.setActive(true);
        place.setUpdatedAt(now);
    }
}
