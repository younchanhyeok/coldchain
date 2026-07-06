package com.coldchain.tracker.domain;

// M1: SAFE/BREACH만 산출(단순 threshold 비교). CAUTION은 M3 이상탐지, RISK는 M4 예측 경고에서 확장.
public enum TrackerStatus {
    SAFE,
    CAUTION,
    RISK,
    BREACH
}
