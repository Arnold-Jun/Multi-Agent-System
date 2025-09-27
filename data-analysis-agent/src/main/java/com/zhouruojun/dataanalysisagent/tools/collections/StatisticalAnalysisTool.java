package com.zhouruojun.dataanalysisagent.tools.collections;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计分析工具
 * 提供描述性统计、相关分析等功能
 */
@Component
@Slf4j
public class StatisticalAnalysisTool {

    @Tool("计算数值列的描述性统计")
    public static String calculateDescriptiveStats(
            @P("数值数据列表") List<Double> values, 
            @P("列名称") String columnName) {
        try {
            if (values == null || values.isEmpty()) {
                return "Error: 数据为空或无效";
            }

            // 过滤掉null值
            List<Double> validValues = values.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (validValues.isEmpty()) {
                return "Error: 没有有效的数值数据";
            }

            DescriptiveStatistics stats = new DescriptiveStatistics();
            validValues.forEach(stats::addValue);

            String result = "列 '" + columnName + "' 的描述性统计:\n" +
                    "- 数据量: " + stats.getN() + "\n" +
                    "- 均值: " + String.format("%.4f", stats.getMean()) + "\n" +
                    "- 中位数: " + String.format("%.4f", stats.getPercentile(50)) + "\n" +
                    "- 标准差: " + String.format("%.4f", stats.getStandardDeviation()) + "\n" +
                    "- 方差: " + String.format("%.4f", stats.getVariance()) + "\n" +
                    "- 最小值: " + String.format("%.4f", stats.getMin()) + "\n" +
                    "- 最大值: " + String.format("%.4f", stats.getMax()) + "\n" +
                    "- 第一四分位数(Q1): " + String.format("%.4f", stats.getPercentile(25)) + "\n" +
                    "- 第三四分位数(Q3): " + String.format("%.4f", stats.getPercentile(75)) + "\n" +
                    "- 偏度: " + String.format("%.4f", stats.getSkewness()) + "\n" +
                    "- 峰度: " + String.format("%.4f", stats.getKurtosis()) + "\n";

            return result;
        } catch (Exception e) {
            log.error("Error calculating descriptive statistics: {}", e.getMessage(), e);
            return "Error calculating descriptive statistics: " + e.getMessage();
        }
    }

    @Tool("计算两个数值列之间的相关系数")
    public static String calculateCorrelation(
            @P("第一列数值数据") List<Double> values1, 
            @P("第二列数值数据") List<Double> values2, 
            @P("第一列的名称") String column1Name, 
            @P("第二列的名称") String column2Name) {
        try {
            if (values1 == null || values2 == null || values1.size() != values2.size()) {
                return "Error: 数据无效或长度不匹配";
            }

            // 过滤配对的有效值
            List<Double> validValues1 = new ArrayList<>();
            List<Double> validValues2 = new ArrayList<>();
            
            for (int i = 0; i < values1.size(); i++) {
                if (values1.get(i) != null && values2.get(i) != null) {
                    validValues1.add(values1.get(i));
                    validValues2.add(values2.get(i));
                }
            }

            if (validValues1.size() < 2) {
                return "Error: 有效数据点数量不足(需要至少2个点)";
            }

            double[] array1 = validValues1.stream().mapToDouble(Double::doubleValue).toArray();
            double[] array2 = validValues2.stream().mapToDouble(Double::doubleValue).toArray();

            PearsonsCorrelation correlation = new PearsonsCorrelation();
            double correlationValue = correlation.correlation(array1, array2);

            StringBuilder result = new StringBuilder();
            result.append("相关性分析结果:\n");
            result.append("- 列1: ").append(column1Name).append("\n");
            result.append("- 列2: ").append(column2Name).append("\n");
            result.append("- 有效数据点: ").append(validValues1.size()).append("\n");
            result.append("- Pearson相关系数: ").append(String.format("%.4f", correlationValue)).append("\n");
            
            // 解释相关性强度
            String strength;
            if (Math.abs(correlationValue) >= 0.8) {
                strength = "强相关";
            } else if (Math.abs(correlationValue) >= 0.5) {
                strength = "中等相关";
            } else if (Math.abs(correlationValue) >= 0.3) {
                strength = "弱相关";
            } else {
                strength = "几乎无相关";
            }
            
            String direction = correlationValue >= 0 ? "正" : "负";
            result.append("- 相关性解释: ").append(direction).append(strength).append("\n");

            return result.toString();
        } catch (Exception e) {
            log.error("Error calculating correlation: {}", e.getMessage(), e);
            return "Error calculating correlation: " + e.getMessage();
        }
    }

    @Tool("检测数值列中的异常值")
    public static String detectOutliers(
            @P("数值数据列表") List<Double> values, 
            @P("列名称") String columnName, 
            @P("检测方法：iqr（四分位距）或zscore（Z分数）") String method) {
        try {
            if (values == null || values.isEmpty()) {
                return "Error: 数据为空或无效";
            }

            List<Double> validValues = values.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (validValues.isEmpty()) {
                return "Error: 没有有效的数值数据";
            }

            DescriptiveStatistics stats = new DescriptiveStatistics();
            validValues.forEach(stats::addValue);

            List<Double> outliers = new ArrayList<>();
            
            if ("iqr".equalsIgnoreCase(method)) {
                // 使用IQR方法检测异常值
                double q1 = stats.getPercentile(25);
                double q3 = stats.getPercentile(75);
                double iqr = q3 - q1;
                double lowerBound = q1 - 1.5 * iqr;
                double upperBound = q3 + 1.5 * iqr;
                
                outliers = validValues.stream()
                        .filter(value -> value < lowerBound || value > upperBound)
                        .collect(Collectors.toList());
                        
            } else if ("zscore".equalsIgnoreCase(method)) {
                // 使用Z-score方法检测异常值
                double mean = stats.getMean();
                double stdDev = stats.getStandardDeviation();
                
                outliers = validValues.stream()
                        .filter(value -> Math.abs((value - mean) / stdDev) > 2)
                        .collect(Collectors.toList());
            }

            StringBuilder result = new StringBuilder();
            result.append("异常值检测结果 (").append(method).append("方法):\n");
            result.append("- 列名: ").append(columnName).append("\n");
            result.append("- 总数据点: ").append(validValues.size()).append("\n");
            result.append("- 检测到的异常值数量: ").append(outliers.size()).append("\n");
            
            if (!outliers.isEmpty()) {
                result.append("- 异常值: ");
                outliers.stream()
                        .limit(20) // 限制显示数量
                        .forEach(value -> result.append(String.format("%.4f ", value)));
                if (outliers.size() > 20) {
                    result.append("...(还有").append(outliers.size() - 20).append("个)");
                }
                result.append("\n");
                
                double outlierPercentage = (outliers.size() * 100.0) / validValues.size();
                result.append("- 异常值占比: ").append(String.format("%.2f%%", outlierPercentage)).append("\n");
            }

            return result.toString();
        } catch (Exception e) {
            log.error("Error detecting outliers: {}", e.getMessage(), e);
            return "Error detecting outliers: " + e.getMessage();
        }
    }

    @Tool("计算数值列的分布分析")
    public static String analyzeDistribution(
            @P("数值数据列表") List<Double> values, 
            @P("列名称") String columnName, 
            @P("分组数量") int bins) {
        try {
            if (values == null || values.isEmpty()) {
                return "Error: 数据为空或无效";
            }

            List<Double> validValues = values.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (validValues.isEmpty()) {
                return "Error: 没有有效的数值数据";
            }

            DescriptiveStatistics stats = new DescriptiveStatistics();
            validValues.forEach(stats::addValue);

            // 创建直方图
            double min = stats.getMin();
            double max = stats.getMax();
            double binWidth = (max - min) / bins;

            Map<String, Integer> histogram = new LinkedHashMap<>();
            
            for (int i = 0; i < bins; i++) {
                double binStart = min + i * binWidth;
                double binEnd = binStart + binWidth;
                String binRange = String.format("[%.2f, %.2f)", binStart, binEnd);
                histogram.put(binRange, 0);
            }

            // 最后一个区间包含最大值
            String lastBinKey = histogram.keySet().stream()
                    .reduce((first, second) -> second).orElse("");
            if (!lastBinKey.isEmpty()) {
                histogram.remove(lastBinKey);
                double binStart = min + (bins - 1) * binWidth;
                double binEnd = max;
                String binRange = String.format("[%.2f, %.2f]", binStart, binEnd);
                histogram.put(binRange, 0);
            }

            // 统计每个区间的频数
            for (Double value : validValues) {
                for (String binRange : histogram.keySet()) {
                    String[] range = binRange.replaceAll("[\\[\\]\\(\\)]", "").split(", ");
                    double binStart = Double.parseDouble(range[0]);
                    double binEnd = Double.parseDouble(range[1]);
                    
                    if ((value >= binStart && value < binEnd) || 
                        (binRange.endsWith("]") && value == binEnd)) {
                        histogram.put(binRange, histogram.get(binRange) + 1);
                        break;
                    }
                }
            }

            StringBuilder result = new StringBuilder();
            result.append("分布分析结果:\n");
            result.append("- 列名: ").append(columnName).append("\n");
            result.append("- 数据点数量: ").append(validValues.size()).append("\n");
            result.append("- 分组数量: ").append(bins).append("\n");
            result.append("\n频数分布:\n");
            
            for (Map.Entry<String, Integer> entry : histogram.entrySet()) {
                double percentage = (entry.getValue() * 100.0) / validValues.size();
                result.append(String.format("  %s: %d (%.1f%%)\n", 
                        entry.getKey(), entry.getValue(), percentage));
            }

            return result.toString();
        } catch (Exception e) {
            log.error("Error analyzing distribution: {}", e.getMessage(), e);
            return "Error analyzing distribution: " + e.getMessage();
        }
    }
}
