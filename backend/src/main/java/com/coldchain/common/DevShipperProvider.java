package com.coldchain.common;

import org.springframework.stereotype.Component;

/**
 * M1 임시 조치: 인증(JWT)이 없는 상태에서 화주 소유권을 채우기 위해
 * V2__seed_dev_shipper.sql로 시드한 단일 dev shipper(id=1)를 반환한다.
 * M5에서 JWT 인증이 도입되면 이 컴포넌트를 인증된 principal 조회로 교체한다.
 */
@Component
public class DevShipperProvider {

    private static final Long DEV_SHIPPER_ID = 1L;

    public Long shipperId() {
        return DEV_SHIPPER_ID;
    }
}
