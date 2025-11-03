package com.zhouruojun.travelingagent.tools.extractor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * get_feed_detail工具结果提取器
 * 只保留核心字段：feed_id、title、desc
 * 可选保留部分评论内容（限制数量以控制上下文长度）
 */
@Slf4j
@Component
public class FeedDetailExtractor implements ToolResultExtractor {
    
    /**
     * 最大保留的主评论数量
     */
    private static final int MAX_MAIN_COMMENTS = 10;
    
    
    /**
     * 日期格式化器（只显示日期，不包含时间）
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @Override
    public String getSupportedToolName() {
        return "get_feed_detail";
    }
    
    @Override
    public String extract(String toolName, String rawResult) {
        if (rawResult == null || rawResult.trim().isEmpty()) {
            return rawResult;
        }
        
        try {
            // 解析JSON
            JSONObject json = JSON.parseObject(rawResult);
            
            // 提取feed_id（顶层）
            String feedId = json.getString("feed_id");
            
            // 提取data.note中的关键字段
            JSONObject data = json.getJSONObject("data");
            if (data == null) {
                return formatResult(feedId, null, null, null);
            }
            
            JSONObject note = data.getJSONObject("note");
            if (note == null) {
                return formatResult(feedId, null, null, null);
            }
            
            String title = note.getString("title");
            String desc = note.getString("desc");
            
            // 提取部分评论（限制数量）
            String commentsText = extractComments(data.getJSONObject("comments"));
            
            return formatResult(feedId, title, desc, commentsText);
            
        } catch (JSONException e) {
            log.warn("解析get_feed_detail结果失败，返回原始结果: {}", e.getMessage());
            return rawResult;
        } catch (Exception e) {
            log.warn("提取get_feed_detail结果时发生错误，返回原始结果: {}", e.getMessage());
            return rawResult;
        }
    }
    
    /**
     * 提取评论内容（限制数量）
     * 保留评论的content和createTime（日期），过滤userInfo、点赞数等
     */
    private String extractComments(JSONObject commentsObj) {
        if (commentsObj == null) {
            return null;
        }
        
        JSONArray commentsList = commentsObj.getJSONArray("list");
        if (commentsList == null || commentsList.isEmpty()) {
            return null;
        }
        
        // 预估容量：每个评论约150字符（包含日期）
        int estimatedSize = Math.min(commentsList.size(), MAX_MAIN_COMMENTS) * 150;
        StringBuilder sb = new StringBuilder(estimatedSize);
        sb.append("【精选评论】\n");
        
        // 只处理前MAX_MAIN_COMMENTS条主评论
        int size = Math.min(commentsList.size(), MAX_MAIN_COMMENTS);
        for (int i = 0; i < size; i++) {
            Object commentObj = commentsList.get(i);
            if (!(commentObj instanceof JSONObject)) {
                continue;
            }
            
            JSONObject comment = (JSONObject) commentObj;
            String content = comment.getString("content");
            
            if (content != null && !content.trim().isEmpty()) {
                // 提取时间并格式化为日期
                String dateStr = formatCommentTime(comment.getLong("createTime"));
                
                sb.append(String.format("%d. [%s] %s\n", i + 1, dateStr, content));
                
                // 提取子评论（每个主评论只有一条子评论）
                JSONArray subComments = comment.getJSONArray("subComments");
                if (subComments != null && !subComments.isEmpty()) {
                    Object subCommentObj = subComments.get(0); // 只取第一条
                    if (subCommentObj instanceof JSONObject) {
                        JSONObject subComment = (JSONObject) subCommentObj;
                        String subContent = subComment.getString("content");
                        if (subContent != null && !subContent.trim().isEmpty()) {
                            String subDateStr = formatCommentTime(subComment.getLong("createTime"));
                            sb.append(String.format("   回复 [%s]: %s\n", subDateStr, subContent));
                        }
                    }
                }
                
                sb.append("\n");
            }
        }
        
        // 检查是否还有更多评论
        boolean hasMore = commentsObj.getBooleanValue("hasMore");
        if (hasMore || commentsList.size() > MAX_MAIN_COMMENTS) {
            sb.append(String.format("（还有更多评论，仅显示前 %d 条）\n", MAX_MAIN_COMMENTS));
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化评论时间戳为日期字符串
     * createTime是Unix时间戳（毫秒），格式化为yyyy-MM-dd格式
     * 
     * @param timestamp 时间戳（毫秒），可能为null
     * @return 格式化后的日期字符串，如果时间戳无效返回"未知日期"
     */
    private String formatCommentTime(Long timestamp) {
        if (timestamp == null || timestamp <= 0) {
            return "未知日期";
        }
        
        try {
            // 将毫秒时间戳转换为Instant，然后转换为本地日期
            Instant instant = Instant.ofEpochMilli(timestamp);
            LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
            return date.format(DATE_FORMATTER);
        } catch (Exception e) {
            log.debug("格式化评论时间失败: {}", e.getMessage());
            return "未知日期";
        }
    }
    
    /**
     * 格式化输出结果
     */
    private String formatResult(String feedId, String title, String desc, String comments) {
        // 预估容量：desc可能很长，评论也有限制
        int estimatedSize = 2000 + (desc != null ? desc.length() : 0) + (comments != null ? comments.length() : 0);
        StringBuilder sb = new StringBuilder(Math.min(estimatedSize, 15000)); // 限制最大15KB
        
        if (feedId != null) {
            sb.append("【笔记ID】: ").append(feedId).append("\n\n");
        }
        
        if (title != null && !title.trim().isEmpty()) {
            sb.append("【标题】: ").append(title).append("\n\n");
        }
        
        if (desc != null && !desc.trim().isEmpty()) {
            sb.append("【内容】:\n").append(desc).append("\n\n");
        }
        
        if (comments != null && !comments.trim().isEmpty()) {
            sb.append(comments);
        }
        
        return sb.toString();
    }
}

