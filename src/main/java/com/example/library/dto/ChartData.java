package com.example.library.dto;

import java.util.List;

/**
 * Labels and values for a single-series Chart.js chart.
 */
public record ChartData(List<String> labels, List<? extends Number> values) {
}
