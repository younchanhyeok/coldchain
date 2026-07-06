package com.coldchain.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.common.GeoPoints;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import com.coldchain.tracker.service.TrackerLatestService;
import com.coldchain.tracker.service.TrackerLatestUpsertOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * ★동시성 사례: tracker_latest 낙관적 락 upsert 검증.
 * 정상 순서/out-of-order 스킵/동시 경쟁(update-update, insert-insert) 재시도까지 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TrackerLatestServiceTest {

    @Autowired
    private TrackerLatestService trackerLatestService;

    @Autowired
    private TrackerLatestRepository trackerLatestRepository;

    @Autowired
    private TrackerRepository trackerRepository;

    private String givenTracker(String trackerId) {
        trackerRepository.save(new Tracker(trackerId, 1L, "백신 A", new BigDecimal("8.0"), "hash"));
        return trackerId;
    }

    @Test
    void firstReadingCreatesTrackerLatest() {
        String trackerId = givenTracker("TRK-LATEST-001");
        Instant recordedAt = Instant.now();

        TrackerLatestUpsertOutcome outcome = trackerLatestService.upsert(
                trackerId, recordedAt, new BigDecimal("5.5"), GeoPoints.of(37.5, 127.0));

        assertThat(outcome).isEqualTo(TrackerLatestUpsertOutcome.UPDATED);
        TrackerLatest stored = trackerLatestRepository.findById(trackerId).orElseThrow();
        assertThat(stored.getLastTs()).isEqualTo(recordedAt);
        assertThat(stored.getLastTemp()).isEqualByComparingTo("5.5");
    }

    @Test
    void outOfOrderReadingDoesNotOverwriteNewerState() {
        String trackerId = givenTracker("TRK-LATEST-002");
        Instant newer = Instant.now();
        Instant older = newer.minusSeconds(60);

        trackerLatestService.upsert(trackerId, newer, new BigDecimal("6.0"), GeoPoints.of(37.5, 127.0));
        TrackerLatestUpsertOutcome outcome = trackerLatestService.upsert(
                trackerId, older, new BigDecimal("1.0"), GeoPoints.of(0, 0));

        assertThat(outcome).isEqualTo(TrackerLatestUpsertOutcome.OUT_OF_ORDER);
        TrackerLatest stored = trackerLatestRepository.findById(trackerId).orElseThrow();
        assertThat(stored.getLastTs()).isEqualTo(newer);
        assertThat(stored.getLastTemp()).isEqualByComparingTo("6.0");
    }

    @Test
    void concurrentUpdatesOnExistingRowResolveToNewestReadingWithoutConflict() throws Exception {
        String trackerId = givenTracker("TRK-LATEST-003");
        Instant seed = Instant.now().minusSeconds(120);
        trackerLatestService.upsert(trackerId, seed, new BigDecimal("4.0"), GeoPoints.of(37.0, 127.0));

        Instant older = seed.plusSeconds(10);
        Instant newer = seed.plusSeconds(20);

        ConcurrentRaceResult result = raceTwoUpserts(trackerId, older, new BigDecimal("5.0"), newer, new BigDecimal("9.0"));

        assertThat(result.outcomeA()).isNotEqualTo(TrackerLatestUpsertOutcome.CONFLICT);
        assertThat(result.outcomeB()).isNotEqualTo(TrackerLatestUpsertOutcome.CONFLICT);

        TrackerLatest stored = trackerLatestRepository.findById(trackerId).orElseThrow();
        assertThat(stored.getLastTs()).isEqualTo(newer);
        assertThat(stored.getLastTemp()).isEqualByComparingTo("9.0");
    }

    @Test
    void concurrentFirstReadingsForNewTrackerResolveWithoutDuplicateKeyError() throws Exception {
        String trackerId = givenTracker("TRK-LATEST-004");
        Instant base = Instant.now();
        Instant older = base;
        Instant newer = base.plusSeconds(10);

        ConcurrentRaceResult result = raceTwoUpserts(trackerId, older, new BigDecimal("5.0"), newer, new BigDecimal("9.0"));

        assertThat(result.outcomeA()).isNotEqualTo(TrackerLatestUpsertOutcome.CONFLICT);
        assertThat(result.outcomeB()).isNotEqualTo(TrackerLatestUpsertOutcome.CONFLICT);

        TrackerLatest stored = trackerLatestRepository.findById(trackerId).orElseThrow();
        assertThat(stored.getLastTs()).isEqualTo(newer);
        assertThat(stored.getLastTemp()).isEqualByComparingTo("9.0");
    }

    private ConcurrentRaceResult raceTwoUpserts(String trackerId, Instant tsA, BigDecimal tempA, Instant tsB, BigDecimal tempB)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<TrackerLatestUpsertOutcome> taskA = () -> {
            ready.countDown();
            go.await();
            return trackerLatestService.upsert(trackerId, tsA, tempA, GeoPoints.of(37.1, 127.1));
        };
        Callable<TrackerLatestUpsertOutcome> taskB = () -> {
            ready.countDown();
            go.await();
            return trackerLatestService.upsert(trackerId, tsB, tempB, GeoPoints.of(37.2, 127.2));
        };

        try {
            Future<TrackerLatestUpsertOutcome> futureA = executor.submit(taskA);
            Future<TrackerLatestUpsertOutcome> futureB = executor.submit(taskB);
            ready.await();
            go.countDown();

            return new ConcurrentRaceResult(
                    futureA.get(10, TimeUnit.SECONDS),
                    futureB.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }
    }

    private record ConcurrentRaceResult(TrackerLatestUpsertOutcome outcomeA, TrackerLatestUpsertOutcome outcomeB) {
    }
}
