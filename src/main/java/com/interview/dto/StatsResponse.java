package com.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class StatsResponse {
    private final int totalSessions;
    private final int completedSessions;
    private final double overallAverageScore;
    private final List<CategoryStat> categoryStats;

    @Getter
    @AllArgsConstructor
    public static class CategoryStat {
        private final String categoryName;
        private final double averageScore;
        private final int answerCount;
    }
}
