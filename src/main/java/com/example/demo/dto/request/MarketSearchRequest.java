package com.example.demo.dto.request;

import java.time.LocalDate;
import java.util.List;

public record MarketSearchRequest (
    String keyword,
    List<String> cities,
    List<String> eventStatuses,
    LocalDate startDate,
    LocalDate endDate,
    List<String> categoryNames,
    String eventType
){
}
