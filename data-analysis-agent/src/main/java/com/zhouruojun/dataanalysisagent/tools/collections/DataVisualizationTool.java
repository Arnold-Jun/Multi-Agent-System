package com.zhouruojun.dataanalysisagent.tools.collections;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 数据可视化工具
 * 生成各种图表和可视化内容
 */
@Component
@Slf4j
public class DataVisualizationTool {

    private static final String CHART_BASE_PATH = "./charts";

    @Tool("生成柱状图")
    public static String generateBarChart(
            @P("数据映射，键为类别，值为数值") Map<String, Number> data, 
            @P("图表标题") String title, 
            @P("X轴标签") String xAxisLabel, 
            @P("Y轴标签") String yAxisLabel) {
        try {
            log.info("Generating bar chart: {}", title);
            
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            for (Map.Entry<String, Number> entry : data.entrySet()) {
                dataset.addValue(entry.getValue(), "Series", entry.getKey());
            }

            JFreeChart chart = ChartFactory.createBarChart(
                    title,
                    xAxisLabel,
                    yAxisLabel,
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );

            // 设置图表样式
            chart.getTitle().setFont(new Font("宋体", Font.BOLD, 18));
            chart.setBackgroundPaint(Color.WHITE);

            String fileName = saveChart(chart, "bar_chart");
            return "柱状图生成成功!\n" +
                   "- 标题: " + title + "\n" +
                   "- 文件路径: " + fileName + "\n" +
                   "- 数据点数: " + data.size();

        } catch (Exception e) {
            log.error("Error generating bar chart: {}", e.getMessage(), e);
            return "Error generating bar chart: " + e.getMessage();
        }
    }

    @Tool("生成饼图")
    public static String generatePieChart(
            @P("数据映射，键为类别，值为数值") Map<String, Number> data, 
            @P("图表标题") String title) {
        try {
            log.info("Generating pie chart: {}", title);
            
            DefaultPieDataset dataset = new DefaultPieDataset();
            for (Map.Entry<String, Number> entry : data.entrySet()) {
                dataset.setValue(entry.getKey(), entry.getValue());
            }

            JFreeChart chart = ChartFactory.createPieChart(
                    title,
                    dataset,
                    true,
                    true,
                    false
            );

            // 设置图表样式
            chart.getTitle().setFont(new Font("宋体", Font.BOLD, 18));
            chart.setBackgroundPaint(Color.WHITE);

            String fileName = saveChart(chart, "pie_chart");
            return "饼图生成成功!\n" +
                   "- 标题: " + title + "\n" +
                   "- 文件路径: " + fileName + "\n" +
                   "- 数据点数: " + data.size();

        } catch (Exception e) {
            log.error("Error generating pie chart: {}", e.getMessage(), e);
            return "Error generating pie chart: " + e.getMessage();
        }
    }

    @Tool("生成散点图")
    public static String generateScatterPlot(
            @P("X轴数值列表") List<Double> xValues, 
            @P("Y轴数值列表") List<Double> yValues, 
            @P("图表标题") String title, 
            @P("X轴标签") String xAxisLabel, 
            @P("Y轴标签") String yAxisLabel) {
        try {
            log.info("Generating scatter plot: {}", title);
            
            if (xValues.size() != yValues.size()) {
                return "Error: X和Y数据点数量不匹配";
            }

            XYSeries series = new XYSeries("Data Points");
            for (int i = 0; i < xValues.size(); i++) {
                if (xValues.get(i) != null && yValues.get(i) != null) {
                    series.add(xValues.get(i), yValues.get(i));
                }
            }

            XYSeriesCollection dataset = new XYSeriesCollection();
            dataset.addSeries(series);

            JFreeChart chart = ChartFactory.createScatterPlot(
                    title,
                    xAxisLabel,
                    yAxisLabel,
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );

            // 设置图表样式
            chart.getTitle().setFont(new Font("宋体", Font.BOLD, 18));
            chart.setBackgroundPaint(Color.WHITE);

            String fileName = saveChart(chart, "scatter_plot");
            return "散点图生成成功!\n" +
                   "- 标题: " + title + "\n" +
                   "- 文件路径: " + fileName + "\n" +
                   "- 数据点数: " + series.getItemCount();

        } catch (Exception e) {
            log.error("Error generating scatter plot: {}", e.getMessage(), e);
            return "Error generating scatter plot: " + e.getMessage();
        }
    }

    @Tool("生成折线图")
    public static String generateLineChart(
            @P("数据映射，键为类别，值为数值") Map<String, Number> data, 
            @P("图表标题") String title, 
            @P("X轴标签") String xAxisLabel, 
            @P("Y轴标签") String yAxisLabel) {
        try {
            log.info("Generating line chart: {}", title);
            
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            for (Map.Entry<String, Number> entry : data.entrySet()) {
                dataset.addValue(entry.getValue(), "Series", entry.getKey());
            }

            JFreeChart chart = ChartFactory.createLineChart(
                    title,
                    xAxisLabel,
                    yAxisLabel,
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );

            // 设置图表样式
            chart.getTitle().setFont(new Font("宋体", Font.BOLD, 18));
            chart.setBackgroundPaint(Color.WHITE);

            String fileName = saveChart(chart, "line_chart");
            return "折线图生成成功!\n" +
                   "- 标题: " + title + "\n" +
                   "- 文件路径: " + fileName + "\n" +
                   "- 数据点数: " + data.size();

        } catch (Exception e) {
            log.error("Error generating line chart: {}", e.getMessage(), e);
            return "Error generating line chart: " + e.getMessage();
        }
    }

    @Tool("生成直方图")
    public static String generateHistogram(
            @P("数值数据列表") List<Double> values, 
            @P("图表标题") String title, 
            @P("X轴标签") String xAxisLabel, 
            @P("分组数量") int bins) {
        try {
            log.info("Generating histogram: {}", title);
            
            if (values == null || values.isEmpty()) {
                return "Error: 数据为空";
            }

            // 过滤有效值
            List<Double> validValues = values.stream()
                    .filter(java.util.Objects::nonNull)
                    .toList();

            if (validValues.isEmpty()) {
                return "Error: 没有有效的数值数据";
            }

            // 计算直方图数据
            double min = validValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = validValues.stream().mapToDouble(Double::doubleValue).max().orElse(1);
            double binWidth = (max - min) / bins;

            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            
            for (int i = 0; i < bins; i++) {
                final double binStart = min + i * binWidth;
                final double binEnd = binStart + binWidth;
                final int binIndex = i;
                final int totalBins = bins;
                String binLabel = String.format("[%.2f, %.2f)", binStart, binEnd);
                
                long count = validValues.stream()
                        .mapToDouble(Double::doubleValue)
                        .filter(value -> value >= binStart && (binIndex == totalBins - 1 ? value <= binEnd : value < binEnd))
                        .count();
                        
                dataset.addValue(count, "Frequency", binLabel);
            }

            JFreeChart chart = ChartFactory.createBarChart(
                    title,
                    xAxisLabel,
                    "频数",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );

            // 设置图表样式
            chart.getTitle().setFont(new Font("宋体", Font.BOLD, 18));
            chart.setBackgroundPaint(Color.WHITE);

            String fileName = saveChart(chart, "histogram");
            return "直方图生成成功!\n" +
                   "- 标题: " + title + "\n" +
                   "- 文件路径: " + fileName + "\n" +
                   "- 数据点数: " + validValues.size() + "\n" +
                   "- 分组数: " + bins;

        } catch (Exception e) {
            log.error("Error generating histogram: {}", e.getMessage(), e);
            return "Error generating histogram: " + e.getMessage();
        }
    }

    private static String saveChart(JFreeChart chart, String chartType) throws IOException {
        // 创建图表目录
        Path chartDir = Paths.get(CHART_BASE_PATH);
        Files.createDirectories(chartDir);

        // 生成文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("%s_%s.png", chartType, timestamp);
        File chartFile = chartDir.resolve(fileName).toFile();

        // 保存图表
        ChartUtils.saveChartAsPNG(chartFile, chart, 800, 600);
        
        return chartFile.getAbsolutePath();
    }

    @Tool("列出已生成的图表文件")
    public static String listChartFiles() {
        try {
            Path chartDir = Paths.get(CHART_BASE_PATH);
            if (!Files.exists(chartDir)) {
                return "图表目录不存在: " + CHART_BASE_PATH;
            }

            StringBuilder result = new StringBuilder();
            result.append("已生成的图表文件:\n");
            result.append("目录: ").append(chartDir.toAbsolutePath()).append("\n\n");

            Files.list(chartDir)
                    .filter(path -> path.toString().toLowerCase().endsWith(".png"))
                    .sorted((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .forEach(path -> {
                        try {
                            String fileName = path.getFileName().toString();
                            long size = Files.size(path);
                            String lastModified = Files.getLastModifiedTime(path).toString();
                            result.append(String.format("- %s (%.1f KB, %s)\n", 
                                    fileName, size / 1024.0, lastModified));
                        } catch (IOException e) {
                            result.append("- ").append(path.getFileName()).append(" (无法读取信息)\n");
                        }
                    });

            return result.toString();
        } catch (Exception e) {
            log.error("Error listing chart files: {}", e.getMessage(), e);
            return "Error listing chart files: " + e.getMessage();
        }
    }
}
