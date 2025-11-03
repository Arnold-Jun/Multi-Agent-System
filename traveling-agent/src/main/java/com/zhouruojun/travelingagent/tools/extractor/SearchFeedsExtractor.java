package com.zhouruojun.travelingagent.tools.extractor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * search_feeds工具结果提取器
 * 只保留xsecToken、id、displayTitle字段，过滤其他冗余信息
 */
@Slf4j
@Component
public class SearchFeedsExtractor implements ToolResultExtractor {
    
    @Override
    public String getSupportedToolName() {
        return "search_feeds";
    }
    
    @Override
    public String extract(String toolName, String rawResult) {
        if (rawResult == null || rawResult.trim().isEmpty()) {
            return rawResult;
        }
        
        try {
            JSONObject json = JSON.parseObject(rawResult.trim());
            JSONArray feedsArray = json.getJSONArray("feeds");
            int count = json.getIntValue("count");
            
            if (feedsArray == null || feedsArray.isEmpty()) {
                return String.format("搜索结果：找到 %d 条内容", count);
            }
            
            int estimatedSize = Math.min(feedsArray.size() * 150, 10000);
            StringBuilder sb = new StringBuilder(estimatedSize);
            sb.append("搜索结果：找到 ").append(count).append(" 条帖子。\n\n");
            
            for (int i = 0; i < feedsArray.size(); i++) {
                Object feedObj = feedsArray.get(i);
                if (!(feedObj instanceof JSONObject)) {
                    continue;
                }
                
                JSONObject feed = (JSONObject) feedObj;
                String id = feed.getString("id");
                String xsecToken = feed.getString("xsecToken");
                
                JSONObject noteCard = feed.getJSONObject("noteCard");
                String displayTitle = noteCard != null ? noteCard.getString("displayTitle") : null;
                
                sb.append("【结果 ").append(i + 1).append("】\n");
                if (displayTitle != null) {
                    sb.append("标题: ").append(displayTitle).append("\n");
                }
                if (id != null) {
                    sb.append("id: ").append(id).append("\n");
                }
                if (xsecToken != null) {
                    sb.append("xsecToken: ").append(xsecToken).append("\n");
                }
                sb.append("\n");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.warn("解析search_feeds结果失败，返回原始结果: {}", e.getMessage());
            return rawResult;
        }
    }
}

