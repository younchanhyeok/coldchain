package com.coldchain.tracker.dto;

import java.util.List;

// coordinates는 GeoJSON 표준 순서([lon, lat])를 따른다 — 단독 좌표 객체(lat/lon 필드)와는 순서가 다르다.
public record GeoJsonLineString(String type, List<List<Double>> coordinates) {

    public static GeoJsonLineString of(List<List<Double>> coordinates) {
        return new GeoJsonLineString("LineString", coordinates);
    }
}
