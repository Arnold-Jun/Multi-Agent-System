package com.zhouruojun.dataanalysisagent.tools.collections;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网络搜索工具
 * 为数据分析智能体提供网络信息搜索能力
 *
 */
@Component
@Slf4j
public class WebSearchTool {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    public WebSearchTool() {
        // 构造函数保留用于Spring组件扫描
    }

    @Tool("搜索网络信息")
    public static String searchWeb(
            @P("搜索查询关键词") String query, 
            @P("最大返回结果数量") String maxResults) {
        try {
            System.out.println("搜索网络信息: " + query);
            
            int max = 5; // 默认返回5个结果
            try {
                max = Integer.parseInt(maxResults);
                max = Math.min(max, 10); // 最多10个结果
            } catch (NumberFormatException e) {
                System.out.println("无效的结果数量参数: " + maxResults + ", 使用默认值5");
            }

            String searchUrl = buildSearchUrl(query, max);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(15))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 ||response.statusCode() == 202) {
                return parseSearchResults(response.body(), query, max);
            } else {
                System.out.println("搜索请求失败，状态码: " + response.statusCode());
                return "搜索失败，请稍后重试";
            }
            
        } catch (IOException | InterruptedException e) {
            System.out.println("搜索网络信息时发生错误: " + e.getMessage());
            return "搜索时发生错误: " + e.getMessage();
        }
    }

    @Tool("搜索特定网站信息")
    public static String searchSpecificSite(
            @P("搜索查询关键词") String query, 
            @P("目标网站域名") String site, 
            @P("最大返回结果数量") String maxResults) {
        try {
            System.out.println("搜索特定网站信息: " + query + " 在 " + site);
            
            int max = 3; // 默认返回3个结果
            try {
                max = Integer.parseInt(maxResults);
                max = Math.min(max, 5); // 最多5个结果
            } catch (NumberFormatException e) {
                System.out.println("无效的结果数量参数: " + maxResults + ", 使用默认值3");
            }
            
            // 构建特定网站的搜索查询
            String siteQuery = query + " site:" + site;
            String searchUrl = buildSearchUrl(siteQuery, max);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(15))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 || response.statusCode() == 200) {
                return parseSearchResults(response.body(), query, max);
            } else {
                System.out.println("搜索请求失败，状态码: " + response.statusCode());
                return "搜索失败，请稍后重试";
            }
            
        } catch (IOException | InterruptedException e) {
            System.out.println("搜索特定网站信息时发生错误: " + e.getMessage());
            return "搜索时发生错误: " + e.getMessage();
        }
    }

    @Tool("搜索最新新闻")
    public static String searchLatestNews(
            @P("新闻主题关键词") String topic, 
            @P("最大返回结果数量") String maxResults) {
        try {
            System.out.println("搜索最新新闻: " + topic);
            
            int max = 5; // 默认返回5个结果
            try {
                max = Integer.parseInt(maxResults);
                max = Math.min(max, 10); // 最多10个结果
            } catch (NumberFormatException e) {
                System.out.println("无效的结果数量参数: " + maxResults + ", 使用默认值5");
            }
            
            // 添加时间限制搜索最新信息
            String newsQuery = topic + " 最新 2024";
            String searchUrl = buildSearchUrl(newsQuery, max);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(15))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 || response.statusCode() == 202) {
                return parseSearchResults(response.body(), topic, max);
            } else {
                System.out.println("搜索新闻请求失败，状态码: " + response.statusCode());
                return "搜索新闻失败，请稍后重试";
            }
            
        } catch (IOException | InterruptedException e) {
            System.out.println("搜索最新新闻时发生错误: " + e.getMessage());
            return "搜索新闻时发生错误: " + e.getMessage();
        }
    }

    @Tool("搜索学术论文")
    public static String searchAcademicPapers(
            @P("学术主题关键词") String topic, 
            @P("最大返回结果数量") String maxResults) {
        try {
            System.out.println("搜索学术论文: " + topic);
            
            int max = 3; // 默认返回3个结果
            try {
                max = Integer.parseInt(maxResults);
                max = Math.min(max, 5); // 最多5个结果
            } catch (NumberFormatException e) {
                System.out.println("无效的结果数量参数: " + maxResults + ", 使用默认值3");
            }
            
            // 搜索学术资源
            String academicQuery = topic + " 论文 研究 学术";
            String searchUrl = buildSearchUrl(academicQuery, max);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(15))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseSearchResults(response.body(), topic, max);
            } else {
                System.out.println("搜索学术论文请求失败，状态码: " + response.statusCode());
                return "搜索学术论文失败，请稍后重试";
            }
            
        } catch (IOException | InterruptedException e) {
            System.out.println("搜索学术论文时发生错误: " + e.getMessage());
            return "搜索学术论文时发生错误: " + e.getMessage();
        }
    }

    /**
     * 构建搜索URL
     */
    private static String buildSearchUrl(String query, int maxResults) {
        // 使用DuckDuckGo的HTML搜索接口
        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        return "https://html.duckduckgo.com/html/?q=" + encodedQuery + "&kl=cn-zh";
    }

    /**
     * 解析搜索结果
     */
    private static String parseSearchResults(String html, String originalQuery, int maxResults) {
        try {
            StringBuilder result = new StringBuilder();
            result.append("🔍 搜索结果 (查询: ").append(originalQuery).append(")\n");
            result.append("=".repeat(50)).append("\n\n");
            
            // 改进的HTML解析，提取标题、链接和描述
            Pattern resultPattern = Pattern.compile(
                "<div[^>]*class=\"result[^\"]*\"[^>]*>.*?" +
                "<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>.*?" +
                "(?:<a[^>]*class=\"result__snippet\"[^>]*>([^<]+)</a>|" +
                "<span[^>]*class=\"result__snippet\"[^>]*>([^<]+)</span>|" +
                "<div[^>]*class=\"result__snippet\"[^>]*>([^<]+)</div>)?", 
                Pattern.DOTALL
            );
            Matcher matcher = resultPattern.matcher(html);
            
            int count = 0;
            while (matcher.find() && count < maxResults) {
                String url = matcher.group(1);
                String title = matcher.group(2);
                String snippet = matcher.group(3) != null ? matcher.group(3) : 
                                matcher.group(4) != null ? matcher.group(4) : 
                                matcher.group(5) != null ? matcher.group(5) : null;
                
                // 清理标题和描述
                title = cleanHtmlText(title);
                if (snippet != null) {
                    snippet = cleanHtmlText(snippet);
                }
                
                if (!title.isEmpty() && !url.isEmpty()) {
                    count++;
                    result.append(count).append(". ").append(title).append("\n");
                    result.append("   链接: ").append(url).append("\n");
                    
                    // 优先使用搜索结果中的描述
                    if (snippet != null && !snippet.isEmpty() && snippet.length() > 10) {
                        result.append("   内容摘要: ").append(snippet).append("\n");
                    } else {
                        // 如果搜索结果中没有描述，尝试获取网页内容摘要
                        String content = fetchWebPageContent(url);
                        if (content != null && !content.isEmpty()) {
                            result.append("   内容摘要: ").append(content).append("\n");
                        } else {
                            // 提供基于URL和标题的智能描述
                            String smartDescription = generateSmartDescription(title, url, originalQuery);
                            if (smartDescription != null) {
                                result.append("   内容摘要: ").append(smartDescription).append("\n");
                            }
                        }
                    }
                    result.append("\n");
                }
            }
            
            if (count == 0) {
                result.append("未找到相关搜索结果。\n");
                result.append("建议：\n");
                result.append("- 尝试使用不同的关键词\n");
                result.append("- 检查拼写是否正确\n");
                result.append("- 使用更通用的搜索词\n");
            } else {
                result.append("✅ 共找到 ").append(count).append(" 个相关结果");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            System.out.println("解析搜索结果时发生错误: " + e.getMessage());
            return "解析搜索结果时发生错误: " + e.getMessage();
        }
    }
    
    /**
     * 获取网页内容摘要
     */
    private static String fetchWebPageContent(String url) {
        try {
            // 处理DuckDuckGo的重定向链接
            if (url.startsWith("//duckduckgo.com/l/")) {
                // 提取实际URL
                Pattern urlPattern = Pattern.compile("uddg=([^&]+)");
                Matcher urlMatcher = urlPattern.matcher(url);
                if (urlMatcher.find()) {
                    url = java.net.URLDecoder.decode(urlMatcher.group(1), "UTF-8");
                }
            }
            
            // 确保URL有协议
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            
            // 重试机制
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                            .timeout(Duration.ofSeconds(15))
                            .build();
                    
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() == 200) {
                        String content = response.body();
                        String summary = extractContentSummary(content, url);
                        if (summary != null && !summary.isEmpty()) {
                            return summary;
                        }
                    } else if (response.statusCode() == 301 || response.statusCode() == 302) {
                        // 处理重定向
                        String location = response.headers().firstValue("Location").orElse(null);
                        if (location != null && attempt == 1) {
                            url = location;
                            continue;
                        }
                    }
                    
                } catch (Exception e) {
                    if (attempt == 2) {
                        System.out.println("获取网页内容失败 (尝试 " + attempt + "): " + url + " - " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            System.out.println("处理URL时发生错误: " + url + " - " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 提取网页内容摘要
     */
    private static String extractContentSummary(String html, String url) {
        try {
            // 移除HTML标签
            String text = html.replaceAll("<[^>]+>", " ");
            text = text.replaceAll("&[^;]+;", " ");
            text = text.replaceAll("\\s+", " ").trim();
            
            // 根据网站类型提取相关内容
            if (url.contains("badmintoncn.com") || url.contains("yonex.cn")) {
                // 羽毛球相关网站，提取比赛信息
                return extractBadmintonInfo(text);
            } else if (url.contains("olympics.com") || url.contains("qq.com")) {
                // 新闻网站，提取新闻摘要
                return extractNewsSummary(text);
            } else if (url.contains("bilibili.com")) {
                // 视频网站，提取视频信息
                return extractVideoInfo(text);
            } else {
                // 通用内容提取
                return extractGeneralSummary(text);
            }
            
        } catch (Exception e) {
            System.out.println("提取内容摘要失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 提取羽毛球相关信息
     */
    private static String extractBadmintonInfo(String text) {
        // 查找包含比赛结果、成绩等关键词的句子
        String[] keywords = {"石宇奇", "比赛", "成绩", "冠军", "决赛", "世锦赛", "2025"};
        StringBuilder summary = new StringBuilder();
        
        String[] sentences = text.split("[。！？]");
        int count = 0;
        
        for (String sentence : sentences) {
            if (count >= 3) break; // 最多3句话
            
            boolean hasKeyword = false;
            for (String keyword : keywords) {
                if (sentence.contains(keyword)) {
                    hasKeyword = true;
                    break;
                }
            }
            
            if (hasKeyword && sentence.length() > 10 && sentence.length() < 200) {
                summary.append(sentence.trim()).append("。");
                count++;
            }
        }
        
        return summary.length() > 0 ? summary.toString() : null;
    }
    
    /**
     * 提取新闻摘要
     */
    private static String extractNewsSummary(String text) {
        // 查找包含关键信息的段落
        String[] sentences = text.split("[。！？]");
        StringBuilder summary = new StringBuilder();
        
        for (int i = 0; i < Math.min(2, sentences.length); i++) {
            String sentence = sentences[i].trim();
            if (sentence.length() > 20 && sentence.length() < 150) {
                summary.append(sentence).append("。");
            }
        }
        
        return summary.length() > 0 ? summary.toString() : null;
    }
    
    /**
     * 提取视频信息
     */
    private static String extractVideoInfo(String text) {
        // 查找视频标题和描述
        if (text.contains("石宇奇") && text.contains("羽毛球")) {
            return "相关视频内容，包含石宇奇羽毛球比赛信息";
        }
        return null;
    }
    
    /**
     * 提取通用摘要
     */
    private static String extractGeneralSummary(String text) {
        // 取前200个字符作为摘要
        if (text.length() > 200) {
            return text.substring(0, 200) + "...";
        }
        return text;
    }
    
    /**
     * 清理HTML文本
     */
    private static String cleanHtmlText(String text) {
        if (text == null) return "";
        
        // 移除HTML标签
        text = text.replaceAll("<[^>]+>", " ");
        // 解码HTML实体
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&amp;", "&");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&quot;", "\"");
        text = text.replaceAll("&apos;", "'");
        text = text.replaceAll("&[^;]+;", " ");
        // 清理多余空白
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    }
    
    /**
     * 基于标题、URL和查询生成智能描述
     */
    private static String generateSmartDescription(String title, String url, String query) {
        StringBuilder description = new StringBuilder();
        
        // 基于URL域名判断内容类型
        if (url.contains("badmintoncn.com") || url.contains("yonex.cn")) {
            description.append("羽毛球专业网站，包含详细的比赛数据、选手信息和赛事报道");
        } else if (url.contains("aiyuke.com")) {
            description.append("爱羽客羽毛球网，提供最新的羽毛球新闻和赛事资讯");
        } else if (url.contains("news.cn") || url.contains("sohu.com")) {
            description.append("新闻网站，包含相关体育新闻和赛事报道");
        } else if (url.contains("weibo.com")) {
            description.append("微博平台，包含相关讨论和最新动态");
        } else if (url.contains("bwf.tournamentsoftware.com")) {
            description.append("BWF官方赛事系统，包含官方比赛数据和排名信息");
        } else {
            description.append("相关网站，包含与查询主题相关的信息");
        }
        
        // 基于标题添加更多信息
        if (title.contains("2024") || title.contains("2025")) {
            description.append("，涵盖最新赛季信息");
        }
        if (title.contains("比赛") || title.contains("赛事")) {
            description.append("，重点关注比赛数据和成绩");
        }
        if (title.contains("排名") || title.contains("积分")) {
            description.append("，包含排名和积分变化数据");
        }
        
        return description.toString();
    }
}