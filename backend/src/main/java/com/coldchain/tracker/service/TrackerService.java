package com.coldchain.tracker.service;

import com.coldchain.auth.AuthenticatedUserProvider;
import com.coldchain.common.error.DuplicateResourceException;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.dto.TrackerRegisterRequest;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.coldchain.tracker.repository.TrackerRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 디바이스 키는 사용자 비밀번호가 아니라 고엔트로피 랜덤 토큰이므로,
 * 고쓰루풋 ingest 경로의 매 요청 검증 비용을 고려해 BCrypt 대신 SHA-256을 사용한다.
 */
@Service
public class TrackerService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TrackerRepository trackerRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public TrackerService(TrackerRepository trackerRepository, AuthenticatedUserProvider authenticatedUserProvider) {
        this.trackerRepository = trackerRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    public TrackerRegisterResponse register(TrackerRegisterRequest request) {
        if (trackerRepository.existsById(request.trackerId())) {
            throw new DuplicateResourceException("이미 등록된 트래커입니다: " + request.trackerId());
        }

        String deviceKey = generateDeviceKey();
        Tracker tracker = new Tracker(
                request.trackerId(),
                authenticatedUserProvider.shipperId(),
                request.productName(),
                request.thresholdTemp(),
                hashDeviceKey(deviceKey));

        try {
            trackerRepository.save(tracker);
        } catch (DataIntegrityViolationException e) {
            // 위의 existsById 확인과 save 사이에 동시에 같은 트래커ID로 등록 요청이 들어온 경우(TOCTOU) —
            // DB 유니크 제약이 잡아준 걸 깔끔한 409로 변환한다.
            throw new DuplicateResourceException("이미 등록된 트래커입니다: " + request.trackerId());
        }

        return new TrackerRegisterResponse(tracker.getId(), deviceKey, tracker.getCreatedAt());
    }

    private static String generateDeviceKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "dk_" + HexFormat.of().formatHex(bytes);
    }

    public static String hashDeviceKey(String rawDeviceKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawDeviceKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
