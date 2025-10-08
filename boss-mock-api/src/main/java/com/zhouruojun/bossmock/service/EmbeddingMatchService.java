package com.zhouruojun.bossmock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 基于文本特征的智能匹配服务
 * 使用基于文本特征的embedding进行语义相似度计算
 */
@Slf4j
@Service
public class EmbeddingMatchService {

    @Autowired
    private SimpleEmbeddingService simpleEmbeddingService;
    
    // 相似度阈值
    private static final double SIMILARITY_THRESHOLD = 0.7;
    private static final double HIGH_SIMILARITY_THRESHOLD = 0.8;

    /**
     * 检查职位名称是否匹配（基于语义相似度）
     */
    public boolean matchesJobTitle(String jobTitle, String searchTitle) {
        if (searchTitle == null || searchTitle.trim().isEmpty()) {
            return true;
        }
        
        if (jobTitle == null || jobTitle.trim().isEmpty()) {
            return false;
        }
        
        // 先尝试精确匹配
        if (jobTitle.toLowerCase().contains(searchTitle.toLowerCase())) {
            return true;
        }
        
        // 使用语义相似度匹配
        double similarity = calculateSemanticSimilarity(jobTitle, searchTitle);
        log.debug("职位名称相似度: {} vs {} = {}", jobTitle, searchTitle, similarity);
        
        return similarity >= SIMILARITY_THRESHOLD;
    }

    /**
     * 检查关键词是否匹配（基于语义相似度）
     */
    public boolean matchesKeywords(Map<String, Object> job, String keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        
        // 组合职位的所有文本信息
        String jobText = buildJobText(job);
        
        // 分割关键词
        String[] keywordArray = keywords.split("\\s+");
        
        // 计算每个关键词的匹配度
        int matchedKeywords = 0;
        for (String keyword : keywordArray) {
            if (matchesKeywordInText(jobText, keyword)) {
                matchedKeywords++;
            }
        }
        
        // 如果匹配的关键词数量超过阈值，则认为匹配
        double matchRatio = (double) matchedKeywords / keywordArray.length;
        log.debug("关键词匹配度: {}/{} = {}", matchedKeywords, keywordArray.length, matchRatio);
        
        return matchRatio >= 0.6; // 至少60%的关键词需要匹配
    }

    /**
     * 检查单个关键词是否在文本中匹配
     */
    private boolean matchesKeywordInText(String text, String keyword) {
        if (text == null || keyword == null) {
            return false;
        }
        
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        
        // 先尝试精确匹配
        if (lowerText.contains(lowerKeyword)) {
            return true;
        }
        
        // 使用语义相似度匹配
        double similarity = calculateSemanticSimilarity(text, keyword);
        return similarity >= HIGH_SIMILARITY_THRESHOLD;
    }

    /**
     * 构建职位的完整文本信息
     */
    private String buildJobText(Map<String, Object> job) {
        StringBuilder jobText = new StringBuilder();
        
        // 基本信息
        jobText.append(job.get("title")).append(" ");
        jobText.append(job.get("company")).append(" ");
        jobText.append(job.get("industry")).append(" ");
        jobText.append(job.get("category")).append(" ");
        
        // 职责描述
        if (job.get("responsibilities") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> responsibilities = (List<String>) job.get("responsibilities");
            jobText.append(String.join(" ", responsibilities)).append(" ");
        }
        
        // 岗位要求
        if (job.get("requirements") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> requirements = (List<String>) job.get("requirements");
            jobText.append(String.join(" ", requirements)).append(" ");
        }
        
        return jobText.toString();
    }

    /**
     * 计算语义相似度（使用基于文本特征的embedding服务）
     */
    private double calculateSemanticSimilarity(String text1, String text2) {
        try {
            double similarity = simpleEmbeddingService.calculateCosineSimilarity(text1, text2);
            log.debug("使用文本特征Embedding计算相似度: '{}' vs '{}' = {}", text1, text2, similarity);
            return similarity;
        } catch (Exception e) {
            log.warn("Embedding相似度计算失败: {}", e.getMessage());
            return 0.0;
        }
    }
}