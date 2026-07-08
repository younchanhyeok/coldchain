package com.coldchain.alert.dto;

import java.util.List;

public record AlertListResponse(List<AlertResponse> content, int page, int size, long totalElements) {
}
