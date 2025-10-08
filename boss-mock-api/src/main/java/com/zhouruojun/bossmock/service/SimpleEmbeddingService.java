package com.zhouruojun.bossmock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 简化的Embedding服务
 * 使用基于文本特征的相似度计算，不依赖外部模型
 */
@Slf4j
@Service
public class SimpleEmbeddingService {

    private final ConcurrentMap<String, float[]> embeddingCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;
    private static final int VECTOR_DIMENSION = 128;

    /**
     * 获取文本的embedding向量（基于文本特征）
     */
    public float[] getEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[VECTOR_DIMENSION];
        }

        // 检查缓存
        String cacheKey = text.toLowerCase().trim();
        if (embeddingCache.containsKey(cacheKey)) {
            return embeddingCache.get(cacheKey);
        }

        // 基于文本特征生成向量
        float[] embedding = generateFeatureBasedEmbedding(text);
        
        // 缓存结果
        cacheEmbedding(cacheKey, embedding);
        
        return embedding;
    }

    /**
     * 计算两个文本的余弦相似度
     */
    public double calculateCosineSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        float[] embedding1 = getEmbedding(text1);
        float[] embedding2 = getEmbedding(text2);

        return calculateCosineSimilarity(embedding1, embedding2);
    }

    /**
     * 计算两个embedding向量的余弦相似度
     */
    public double calculateCosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null || vector1.length != vector2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        double similarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
        log.debug("余弦相似度计算: {} -> {}", Arrays.toString(vector1), similarity);
        
        return similarity;
    }

    /**
     * 检查两个文本是否相似
     */
    public boolean isSimilar(String text1, String text2, double threshold) {
        double similarity = calculateCosineSimilarity(text1, text2);
        boolean isSimilar = similarity >= threshold;
        
        log.debug("相似度检查: '{}' vs '{}' = {} (阈值: {}) -> {}", 
                text1, text2, similarity, threshold, isSimilar);
        
        return isSimilar;
    }

    /**
     * 基于文本特征生成embedding向量
     */
    private float[] generateFeatureBasedEmbedding(String text) {
        float[] embedding = new float[VECTOR_DIMENSION];
        String lowerText = text.toLowerCase();
        
        // 基于多种文本特征生成向量
        int featureIndex = 0;
        
        // 1. 字符频率特征 (0-25)
        for (int i = 0; i < 26 && featureIndex < VECTOR_DIMENSION; i++) {
            char c = (char) ('a' + i);
            int count = countChar(lowerText, c);
            embedding[featureIndex++] = (float) count / text.length();
        }
        
        // 2. 词汇长度特征 (26-35)
        String[] words = lowerText.split("\\s+");
        for (int i = 0; i < 10 && featureIndex < VECTOR_DIMENSION; i++) {
            int wordCount = 0;
            for (String word : words) {
                if (word.length() == i + 1) {
                    wordCount++;
                }
            }
            embedding[featureIndex++] = (float) wordCount / words.length;
        }
        
        // 3. 技术关键词特征 (36-60)
        String[] techKeywords = {
            "java", "python", "javascript", "ai", "machine", "learning", "deep", "neural",
            "algorithm", "data", "database", "sql", "nosql", "redis", "mongodb", "spring",
            "react", "vue", "angular", "node", "docker", "kubernetes", "aws", "azure", "cloud"
        };
        
        for (String keyword : techKeywords) {
            if (featureIndex < VECTOR_DIMENSION) {
                embedding[featureIndex++] = lowerText.contains(keyword) ? 1.0f : 0.0f;
            }
        }
        
        // 4. 职位相关特征 (61-80)
        String[] jobKeywords = {
            "engineer", "developer", "programmer", "architect", "manager", "analyst", "designer",
            "scientist", "specialist", "consultant", "lead", "senior", "junior", "intern",
            "full", "stack", "frontend", "backend", "mobile", "web"
        };
        
        for (String keyword : jobKeywords) {
            if (featureIndex < VECTOR_DIMENSION) {
                embedding[featureIndex++] = lowerText.contains(keyword) ? 1.0f : 0.0f;
            }
        }
        
        // 5. 行业特征 (81-100)
        String[] industryKeywords = {
            "internet", "finance", "education", "healthcare", "ecommerce", "gaming", "media",
            "automotive", "manufacturing", "retail", "logistics", "real", "estate", "travel",
            "food", "energy", "telecom", "consulting", "government", "nonprofit"
        };
        
        for (String keyword : industryKeywords) {
            if (featureIndex < VECTOR_DIMENSION) {
                embedding[featureIndex++] = lowerText.contains(keyword) ? 1.0f : 0.0f;
            }
        }
        
        // 6. 随机特征填充剩余维度 (101-127)
        java.util.Random random = new java.util.Random(text.hashCode());
        while (featureIndex < VECTOR_DIMENSION) {
            embedding[featureIndex++] = (float) random.nextGaussian();
        }
        
        // 归一化向量
        normalizeVector(embedding);
        
        return embedding;
    }

    /**
     * 计算字符出现次数
     */
    private int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    /**
     * 归一化向量
     */
    private void normalizeVector(float[] vector) {
        float norm = 0.0f;
        for (float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    /**
     * 缓存embedding向量
     */
    private void cacheEmbedding(String key, float[] embedding) {
        if (embeddingCache.size() >= MAX_CACHE_SIZE) {
            // 简单的LRU策略：清除一半缓存
            int removeCount = MAX_CACHE_SIZE / 2;
            embeddingCache.entrySet().stream()
                    .limit(removeCount)
                    .forEach(entry -> embeddingCache.remove(entry.getKey()));
        }
        
        embeddingCache.put(key, embedding);
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        return String.format("SimpleEmbedding缓存: %d/%d 条目", 
                embeddingCache.size(), MAX_CACHE_SIZE);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        embeddingCache.clear();
        log.info("SimpleEmbedding缓存已清空");
    }

    /**
     * 检查模型是否可用（总是返回true，因为不依赖外部模型）
     */
    public boolean isModelAvailable() {
        return true;
    }
}
