package com.zhouruojun.dataanalysisagent.tools.collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 数据加载工具
 * 支持CSV、Excel、JSON等格式的数据加载
 */
@Component
@Slf4j
public class DataLoaderTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Tool("加载CSV文件数据")
    public static String loadCsvData(
            @P("CSV文件的完整路径") String filePath, 
            @P("是否包含表头，true表示第一行是列名") boolean hasHeader, 
            @P("字段分隔符，默认为逗号") String delimiter) {
        try {
            log.info("Loading CSV file: {}", filePath);
            
            CSVFormat format = CSVFormat.DEFAULT;
            if (hasHeader) {
                format = format.withFirstRecordAsHeader();
            }
            if (delimiter != null && !delimiter.isEmpty()) {
                format = format.withDelimiter(delimiter.charAt(0));
            }

            List<Map<String, Object>> data = new ArrayList<>();
            try (CSVParser parser = new CSVParser(new FileReader(filePath), format)) {
                for (CSVRecord record : parser) {
                    Map<String, Object> row = new HashMap<>();
                    if (hasHeader) {
                        for (String header : parser.getHeaderNames()) {
                            row.put(header, record.get(header));
                        }
                    } else {
                        for (int i = 0; i < record.size(); i++) {
                            row.put("column_" + i, record.get(i));
                        }
                    }
                    data.add(row);
                }
            }

            return formatDataSummary(data, "CSV", filePath);
        } catch (Exception e) {
            log.error("Error loading CSV file: {}", e.getMessage(), e);
            return "Error loading CSV file: " + e.getMessage();
        }
    }

    @Tool("加载Excel文件数据")
    public static String loadExcelData(
            @P("Excel文件的完整路径") String filePath, 
            @P("工作表名称，如果为空则使用第一个工作表") String sheetName, 
            @P("是否包含表头，true表示第一行是列名") boolean hasHeader) {
        try {
            log.info("Loading Excel file: {}, sheet: {}", filePath, sheetName);
            
            List<Map<String, Object>> data = new ArrayList<>();
            try (FileInputStream fis = new FileInputStream(filePath)) {
                Workbook workbook;
                if (filePath.endsWith(".xlsx")) {
                    workbook = new XSSFWorkbook(fis);
                } else {
                    workbook = new HSSFWorkbook(fis);
                }

                Sheet sheet;
                if (sheetName != null && !sheetName.isEmpty()) {
                    sheet = workbook.getSheet(sheetName);
                } else {
                    sheet = workbook.getSheetAt(0);
                }

                if (sheet == null) {
                    return "Sheet not found: " + sheetName;
                }

                List<String> headers = new ArrayList<>();
                int startRow = 0;
                
                if (hasHeader && sheet.getPhysicalNumberOfRows() > 0) {
                    Row headerRow = sheet.getRow(0);
                    if (headerRow != null) {
                        for (Cell cell : headerRow) {
                            headers.add(getCellValueAsString(cell));
                        }
                        startRow = 1;
                    }
                }

                for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row != null) {
                        Map<String, Object> rowData = new HashMap<>();
                        for (int j = 0; j < row.getLastCellNum(); j++) {
                            Cell cell = row.getCell(j);
                            String key = hasHeader && j < headers.size() ? 
                                headers.get(j) : "column_" + j;
                            rowData.put(key, getCellValueAsString(cell));
                        }
                        data.add(rowData);
                    }
                }
                workbook.close();
            }

            return formatDataSummary(data, "Excel", filePath);
        } catch (Exception e) {
            log.error("Error loading Excel file: {}", e.getMessage(), e);
            return "Error loading Excel file: " + e.getMessage();
        }
    }

    @Tool("加载JSON文件数据")
    public static String loadJsonData(@P("JSON文件的完整路径") String filePath) {
        try {
            log.info("Loading JSON file: {}", filePath);
            
            String content = Files.readString(Path.of(filePath));
            JsonNode rootNode = objectMapper.readTree(content);
            
            List<Map<String, Object>> data = new ArrayList<>();
            
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    data.add(objectMapper.convertValue(node, Map.class));
                }
            } else if (rootNode.isObject()) {
                data.add(objectMapper.convertValue(rootNode, Map.class));
            }

            return formatDataSummary(data, "JSON", filePath);
        } catch (Exception e) {
            log.error("Error loading JSON file: {}", e.getMessage(), e);
            return "Error loading JSON file: " + e.getMessage();
        }
    }

    @Tool("获取文件信息")
    public static String getFileInfo(@P("文件的完整路径") String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return "File not found: " + filePath;
            }

            StringBuilder info = new StringBuilder();
            info.append("文件信息:\n");
            info.append("- 文件路径: ").append(filePath).append("\n");
            info.append("- 文件大小: ").append(formatFileSize(file.length())).append("\n");
            info.append("- 最后修改时间: ").append(new Date(file.lastModified())).append("\n");
            
            String extension = getFileExtension(filePath);
            info.append("- 文件类型: ").append(extension).append("\n");
            
            if (extension.equals("csv")) {
                info.append("- 支持的操作: 加载CSV数据\n");
            } else if (extension.equals("xlsx") || extension.equals("xls")) {
                info.append("- 支持的操作: 加载Excel数据\n");
            } else if (extension.equals("json")) {
                info.append("- 支持的操作: 加载JSON数据\n");
            }

            return info.toString();
        } catch (Exception e) {
            return "Error getting file info: " + e.getMessage();
        }
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private static String formatDataSummary(List<Map<String, Object>> data, String format, String filePath) {
        StringBuilder summary = new StringBuilder();
        summary.append("数据加载完成!\n");
        summary.append("- 文件格式: ").append(format).append("\n");
        summary.append("- 文件路径: ").append(filePath).append("\n");
        summary.append("- 数据行数: ").append(data.size()).append("\n");
        
        if (!data.isEmpty()) {
            Set<String> columns = data.get(0).keySet();
            summary.append("- 列数: ").append(columns.size()).append("\n");
            summary.append("- 列名: ").append(String.join(", ", columns)).append("\n");
            
            // 显示前几行数据作为示例
            summary.append("\n前3行数据示例:\n");
            for (int i = 0; i < Math.min(3, data.size()); i++) {
                summary.append("第").append(i + 1).append("行: ").append(data.get(i)).append("\n");
            }
        }
        
        return summary.toString();
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String getFileExtension(String filePath) {
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filePath.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }
}
