package com.zhouruojun.bossmock.controller;

import com.zhouruojun.bossmock.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 简化的职位控制器
 * 专门为job-search-agent提供职位查询和投递API
 * 不依赖数据库，使用模拟数据
 */
@Slf4j
@RestController
@RequestMapping("/api/simple")
@Tag(name = "简化职位API", description = "为job-search-agent提供的简化职位接口")
public class SimpleJobController {

    /**
     * 搜索职位 - 完善版本
     * 对应JobInfoCollectionTool.searchJobs方法的需求
     */
    @GetMapping("/jobs/search")
    @Operation(summary = "搜索职位", description = "根据多种条件搜索职位，支持城市、岗位名称、薪资范围、岗位类别、经验要求、学历要求、公司规模等")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchJobs(
            @Parameter(description = "城市") @RequestParam(required = false) String city,
            @Parameter(description = "岗位名称") @RequestParam(required = false) String jobTitle,
            @Parameter(description = "薪资范围") @RequestParam(required = false) String salaryRange,
            @Parameter(description = "岗位类别：实习、校招、社招") @RequestParam(required = false) String jobCategory,
            @Parameter(description = "经验要求") @RequestParam(required = false) String experience,
            @Parameter(description = "学历要求") @RequestParam(required = false) String education,
            @Parameter(description = "公司规模") @RequestParam(required = false) String companyScale,
            @Parameter(description = "行业") @RequestParam(required = false) String industry,
            @Parameter(description = "关键词") @RequestParam(required = false) String keywords,
            @Parameter(description = "排序方式：salary-薪资, time-时间, popularity-热度") @RequestParam(defaultValue = "time") String sortBy,
            @Parameter(description = "排序顺序：asc-升序, desc-降序") @RequestParam(defaultValue = "desc") String sortOrder,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        
        log.info("搜索职位: 城市={}, 岗位名称={}, 薪资={}, 类别={}, 经验={}, 学历={}, 公司规模={}, 行业={}, 关键词={}, 排序={}, 页码={}, 大小={}", 
                city, jobTitle, salaryRange, jobCategory, experience, education, companyScale, industry, keywords, sortBy, page, size);
        
        try {
            // 生成模拟职位数据并进行搜索和分页
            List<Map<String, Object>> allJobs = generateJobDataset();
            log.info("生成的总职位数: {}", allJobs.size());
            
            List<Map<String, Object>> filteredJobs = filterJobs(allJobs, city, jobTitle, salaryRange, jobCategory, 
                    experience, education, companyScale, industry, keywords);
            log.info("过滤后的职位数: {}", filteredJobs.size());
            
            // 排序处理
            filteredJobs = sortJobs(filteredJobs, sortBy, sortOrder);
            
            // 分页处理
            int totalJobs = filteredJobs.size();
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, totalJobs);
            
            List<Map<String, Object>> pagedJobs = filteredJobs.subList(startIndex, endIndex);
            log.info("当前页实际返回职位数: {}", pagedJobs.size());
            
            Map<String, Object> result = new HashMap<>();
            result.put("jobs", pagedJobs);
            result.put("total", totalJobs);
            result.put("page", page);
            result.put("size", pagedJobs.size()); // 修复：使用实际返回的职位数量
            result.put("totalPages", (int) Math.ceil((double) totalJobs / size));
            Map<String, Object> searchConditions = new HashMap<>();
            searchConditions.put("city", city != null ? city : "全国");
            searchConditions.put("jobTitle", jobTitle != null ? jobTitle : "全部岗位");
            searchConditions.put("salaryRange", salaryRange != null ? salaryRange : "不限");
            searchConditions.put("jobCategory", jobCategory != null ? jobCategory : "全部类别");
            searchConditions.put("experience", experience != null ? experience : "不限");
            searchConditions.put("education", education != null ? education : "不限");
            searchConditions.put("companyScale", companyScale != null ? companyScale : "不限");
            searchConditions.put("industry", industry != null ? industry : "不限");
            searchConditions.put("keywords", keywords != null ? keywords : "无");
            searchConditions.put("sortBy", sortBy);
            searchConditions.put("sortOrder", sortOrder);
            result.put("searchConditions", searchConditions);
            result.put("searchTime", LocalDateTime.now());
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            log.error("搜索职位失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("搜索职位失败: " + e.getMessage()));
        }
    }

    /**
     * 投递职位 - 完善版本
     * 对应JobInfoCollectionTool.applyForJob方法的需求
     */
    @PostMapping("/jobs/{jobId}/apply")
    @Operation(summary = "投递职位", description = "向指定职位投递简历，支持简历信息和求职信")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyForJob(
            @Parameter(description = "职位ID") @PathVariable String jobId,
            @Parameter(description = "简历信息") @RequestParam String resumeInfo,
            @Parameter(description = "求职信") @RequestParam(required = false) String coverLetter,
            @Parameter(description = "用户ID") @RequestParam(required = false) String userId) {
        
        log.info("投递职位: 岗位ID={}, 用户ID={}, 简历信息长度={}, 求职信长度={}", 
                jobId, userId, 
                resumeInfo != null ? resumeInfo.length() : 0, 
                coverLetter != null ? coverLetter.length() : 0);
        
        try {
            // 验证职位是否存在
            List<Map<String, Object>> allJobs = generateJobDataset();
            Map<String, Object> targetJob = allJobs.stream()
                    .filter(job -> jobId.equals(job.get("jobId")))
                    .findFirst()
                    .orElse(null);
            
            if (targetJob == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("职位不存在: " + jobId));
            }
            
            // 检查职位状态
            if (!"招聘中".equals(targetJob.get("status"))) {
                return ResponseEntity.badRequest().body(ApiResponse.error("该职位已停止招聘"));
            }
            
            // 生成投递结果
            Map<String, Object> result = generateRealisticApplicationResult(jobId, resumeInfo, coverLetter, targetJob, userId);
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            log.error("投递职位失败: jobId={}", jobId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("投递职位失败: " + e.getMessage()));
        }
    }

    /**
     * 获取职位详情 - 简化版本
     */
    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "获取职位详情", description = "获取指定职位的详细信息")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJobDetails(@PathVariable String jobId) {
        log.info("获取职位详情: jobId={}", jobId);
        
        try {
            Map<String, Object> jobDetail = generateMockJobDetail(jobId);
            return ResponseEntity.ok(ApiResponse.success(jobDetail));
        } catch (Exception e) {
            log.error("获取职位详情失败: jobId={}", jobId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("获取职位详情失败: " + e.getMessage()));
        }
    }

    /**
     * 获取公司信息 - 简化版本
     */
    @GetMapping("/companies/info")
    @Operation(summary = "获取公司信息", description = "根据公司名称获取公司详细信息")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompanyInfo(
            @Parameter(description = "公司名称") @RequestParam String companyName) {
        log.info("获取公司信息: 公司名称={}", companyName);
        
        try {
            Map<String, Object> companyInfo = generateMockCompanyInfo(companyName);
            return ResponseEntity.ok(ApiResponse.success(companyInfo));
        } catch (Exception e) {
            log.error("获取公司信息失败: companyName={}", companyName, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("获取公司信息失败: " + e.getMessage()));
        }
    }

    /**
     * 测试同义词匹配功能
     */
    @GetMapping("/test/synonym-matching")
    @Operation(summary = "测试同义词匹配", description = "测试职位类别和经验要求的同义词匹配功能")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testSynonymMatching() {
        log.info("测试同义词匹配功能");
        
        try {
            Map<String, Object> testResults = new HashMap<>();
            
            // 测试职位类别同义词匹配
            Map<String, Object> categoryTests = new HashMap<>();
            categoryTests.put("校招 vs 校园招聘", matchesCategory("校园招聘", "校招"));
            categoryTests.put("校园招聘 vs 校招", matchesCategory("校招", "校园招聘"));
            categoryTests.put("社招 vs 社会招聘", matchesCategory("社会招聘", "社招"));
            categoryTests.put("社会招聘 vs 社招", matchesCategory("社招", "社会招聘"));
            categoryTests.put("实习 vs 实习生", matchesCategory("实习生", "实习"));
            categoryTests.put("实习生 vs 实习", matchesCategory("实习", "实习生"));
            testResults.put("categoryMatching", categoryTests);
            
            // 测试经验要求同义词匹配
            Map<String, Object> experienceTests = new HashMap<>();
            experienceTests.put("应届毕业生 vs 应届生", matchesExperienceRobust("应届生", "应届毕业生"));
            experienceTests.put("应届生 vs 应届毕业生", matchesExperienceRobust("应届毕业生", "应届生"));
            experienceTests.put("1-3年 vs 1到3年", matchesExperienceRobust("1到3年", "1-3年"));
            experienceTests.put("1到3年 vs 1-3年", matchesExperienceRobust("1-3年", "1到3年"));
            experienceTests.put("3-5年 vs 中级", matchesExperienceRobust("中级", "3-5年"));
            experienceTests.put("中级 vs 3-5年", matchesExperienceRobust("3-5年", "中级"));
            testResults.put("experienceMatching", experienceTests);
            
            // 测试实际职位数据
            List<Map<String, Object>> allJobs = generateJobDataset();
            Map<String, Object> realJobTests = new HashMap<>();
            
            // 测试前3个AI Agent开发工程师职位
            for (int i = 0; i < Math.min(3, allJobs.size()); i++) {
                Map<String, Object> job = allJobs.get(i);
                String jobTitle = (String) job.get("title");
                String jobCategory = (String) job.get("category");
                String jobCity = (String) job.get("city");
                
                if ("AI Agent开发工程师".equals(jobTitle)) {
                    Map<String, Object> jobTest = new HashMap<>();
                    jobTest.put("title", jobTitle);
                    jobTest.put("category", jobCategory);
                    jobTest.put("city", jobCity);
                    jobTest.put("matches校招", matchesCategory(jobCategory, "校招"));
                    jobTest.put("matches校园招聘", matchesCategory(jobCategory, "校园招聘"));
                    jobTest.put("matches应届生招聘", matchesCategory(jobCategory, "应届生招聘"));
                    realJobTests.put("job_" + (i + 1), jobTest);
                }
            }
            testResults.put("realJobTests", realJobTests);
            
            return ResponseEntity.ok(ApiResponse.success(testResults));
            
        } catch (Exception e) {
            log.error("测试同义词匹配失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("测试同义词匹配失败: " + e.getMessage()));
        }
    }


    // 缓存生成的职位数据，避免重复生成
    private static List<Map<String, Object>> cachedJobs = null;
    
    // 职位类别同义词映射表
    private static final Map<String, Set<String>> CATEGORY_SYNONYMS = new HashMap<>();
    static {
        // 校招相关同义词
        Set<String> schoolRecruitment = new HashSet<>();
        schoolRecruitment.add("校招");
        schoolRecruitment.add("校园招聘");
        schoolRecruitment.add("应届生招聘");
        schoolRecruitment.add("毕业生招聘");
        schoolRecruitment.add("校园");
        CATEGORY_SYNONYMS.put("校招", schoolRecruitment);
        
        // 社招相关同义词
        Set<String> socialRecruitment = new HashSet<>();
        socialRecruitment.add("社招");
        socialRecruitment.add("社会招聘");
        socialRecruitment.add("在职招聘");
        socialRecruitment.add("社会");
        CATEGORY_SYNONYMS.put("社招", socialRecruitment);
        
        // 实习相关同义词
        Set<String> internship = new HashSet<>();
        internship.add("实习");
        internship.add("实习生");
        internship.add("实习招聘");
        internship.add("实习岗位");
        CATEGORY_SYNONYMS.put("实习", internship);
    }
    
    // 经验要求同义词映射表
    private static final Map<String, Set<String>> EXPERIENCE_SYNONYMS = new HashMap<>();
    static {
        // 应届生相关
        Set<String> freshGraduate = new HashSet<>();
        freshGraduate.add("应届毕业生");
        freshGraduate.add("应届生");
        freshGraduate.add("毕业生");
        freshGraduate.add("应届");
        EXPERIENCE_SYNONYMS.put("应届毕业生", freshGraduate);
        
        // 在校学生相关
        Set<String> student = new HashSet<>();
        student.add("在校学生");
        student.add("学生");
        student.add("在校");
        EXPERIENCE_SYNONYMS.put("在校学生", student);
        
        // 1-3年相关
        Set<String> junior = new HashSet<>();
        junior.add("1-3年");
        junior.add("1到3年");
        junior.add("1至3年");
        junior.add("初级");
        junior.add("junior");
        EXPERIENCE_SYNONYMS.put("1-3年", junior);
        
        // 3-5年相关
        Set<String> mid = new HashSet<>();
        mid.add("3-5年");
        mid.add("3到5年");
        mid.add("3至5年");
        mid.add("中级");
        mid.add("middle");
        EXPERIENCE_SYNONYMS.put("3-5年", mid);
        
        // 5-10年相关
        Set<String> senior = new HashSet<>();
        senior.add("5-10年");
        senior.add("5到10年");
        senior.add("5至10年");
        senior.add("高级");
        senior.add("senior");
        EXPERIENCE_SYNONYMS.put("5-10年", senior);
        
        // 10年以上相关
        Set<String> expert = new HashSet<>();
        expert.add("10年以上");
        expert.add("10年+");
        expert.add("专家级");
        expert.add("expert");
        EXPERIENCE_SYNONYMS.put("10年以上", expert);
    }
    
    /**
     * 智能匹配职位类别（支持同义词）
     */
    private boolean matchesCategory(String jobCategory, String searchCategory) {
        if (searchCategory == null || searchCategory.trim().isEmpty()) {
            return true;
        }
        
        if (jobCategory == null) {
            return false;
        }
        
        // 先尝试精确匹配
        if (jobCategory.equals(searchCategory)) {
            return true;
        }
        
        // 尝试同义词匹配
        for (Map.Entry<String, Set<String>> entry : CATEGORY_SYNONYMS.entrySet()) {
            String standardCategory = entry.getKey();
            Set<String> synonyms = entry.getValue();
            
            // 检查搜索词是否在某个同义词组中
            if (synonyms.contains(searchCategory)) {
                // 检查职位类别是否也在同一个同义词组中
                if (synonyms.contains(jobCategory) || jobCategory.equals(standardCategory)) {
                    log.debug("类别同义词匹配成功: 搜索={}, 职位={}, 标准={}", searchCategory, jobCategory, standardCategory);
                    return true;
                }
            }
            
            // 检查职位类别是否在某个同义词组中
            if (synonyms.contains(jobCategory)) {
                // 检查搜索词是否也在同一个同义词组中
                if (synonyms.contains(searchCategory) || searchCategory.equals(standardCategory)) {
                    log.debug("类别同义词匹配成功: 搜索={}, 职位={}, 标准={}", searchCategory, jobCategory, standardCategory);
                    return true;
                }
            }
        }
        
        // 尝试包含匹配（更宽松的匹配）
        if (jobCategory.contains(searchCategory) || searchCategory.contains(jobCategory)) {
            log.debug("类别包含匹配成功: 搜索={}, 职位={}", searchCategory, jobCategory);
            return true;
        }
        
        log.debug("类别匹配失败: 搜索={}, 职位={}", searchCategory, jobCategory);
        return false;
    }
    
    /**
     * 智能匹配经验要求（支持同义词）
     */
    private boolean matchesExperienceRobust(String jobExperience, String searchExperience) {
        if (searchExperience == null || searchExperience.trim().isEmpty()) {
            return true;
        }
        
        if (jobExperience == null) {
            return false;
        }
        
        // 先尝试精确匹配
        if (jobExperience.equals(searchExperience)) {
            return true;
        }
        
        // 尝试同义词匹配
        for (Map.Entry<String, Set<String>> entry : EXPERIENCE_SYNONYMS.entrySet()) {
            String standardExperience = entry.getKey();
            Set<String> synonyms = entry.getValue();
            
            // 检查搜索词是否在某个同义词组中
            if (synonyms.contains(searchExperience)) {
                // 检查职位经验是否也在同一个同义词组中
                if (synonyms.contains(jobExperience) || jobExperience.equals(standardExperience)) {
                    log.debug("经验同义词匹配成功: 搜索={}, 职位={}, 标准={}", searchExperience, jobExperience, standardExperience);
                    return true;
                }
            }
            
            // 检查职位经验是否在某个同义词组中
            if (synonyms.contains(jobExperience)) {
                // 检查搜索词是否也在同一个同义词组中
                if (synonyms.contains(searchExperience) || searchExperience.equals(standardExperience)) {
                    log.debug("经验同义词匹配成功: 搜索={}, 职位={}, 标准={}", searchExperience, jobExperience, standardExperience);
                    return true;
                }
            }
        }
        
        // 尝试包含匹配（更宽松的匹配）
        if (jobExperience.contains(searchExperience) || searchExperience.contains(jobExperience)) {
            log.debug("经验包含匹配成功: 搜索={}, 职位={}", searchExperience, jobExperience);
            return true;
        }
        
        log.debug("经验匹配失败: 搜索={}, 职位={}", searchExperience, jobExperience);
        return false;
    }
    
    /**
     * 生成职位数据集（20个职位）
     */
    private List<Map<String, Object>> generateJobDataset() {
        if (cachedJobs != null) {
            return cachedJobs;
        }
        
        log.info("开始生成2000个职位数据...");
        List<Map<String, Object>> jobs = new ArrayList<>();
        
        // 扩展的数据源 - 更丰富的公司列表
        String[] companies = {
            // 互联网巨头
            "阿里巴巴集团", "腾讯科技", "百度在线", "字节跳动", "美团点评", "京东集团", "网易公司", "新浪微博",
            "滴滴出行", "小米科技", "华为技术", "OPPO广东", "vivo通信", "中兴通讯", "联想集团", "海康威视",
            
            // AI/科技公司
            "大疆创新", "商汤科技", "旷视科技", "依图科技", "云从科技", "思必驰", "出门问问", "地平线",
            "第四范式", "明略科技", "容联云通讯", "声网Agora", "极光JIGUANG", "个推", "友盟+", "神策数据",
            
            // 金融科技
            "蚂蚁金服", "陆金所", "平安科技", "招商银行", "工商银行", "建设银行", "中国银行", "农业银行",
            "中信银行", "民生银行", "光大银行", "华夏银行", "浦发银行", "兴业银行", "广发银行", "中金公司",
            "国泰君安", "海通证券", "中信证券", "华泰证券", "招商证券", "广发证券", "申万宏源", "东方证券",
            "微众银行", "网商银行", "京东数科", "度小满金融", "360金融", "乐信", "趣店", "宜人贷",
            
            // 汽车/新能源
            "上汽集团", "一汽集团", "东风汽车", "北汽集团", "广汽集团", "长城汽车", "吉利汽车", "比亚迪",
            "蔚来汽车", "小鹏汽车", "理想汽车", "威马汽车", "哪吒汽车", "零跑汽车", "爱驰汽车", "拜腾汽车",
            "特斯拉中国", "宝马中国", "奔驰中国", "奥迪中国", "大众中国", "丰田中国", "本田中国", "日产中国",
            
            // 游戏/娱乐
            "米哈游", "莉莉丝游戏", "叠纸游戏", "鹰角网络", "心动网络", "三七互娱", "完美世界", "巨人网络",
            "盛趣游戏", "游族网络", "恺英网络", "掌趣科技", "中手游", "紫龙游戏", "多益网络", "网龙网络",
            
            // 教育/在线教育
            "新东方", "好未来", "猿辅导", "作业帮", "VIPKID", "掌门教育", "高途课堂", "网易有道",
            "腾讯教育", "百度教育", "字节教育", "松鼠AI", "流利说", "英语流利说", "沪江网校", "51Talk",
            
            // 医疗/生物科技
            "药明康德", "恒瑞医药", "迈瑞医疗", "华大基因", "贝瑞和康", "燃石医学", "泛生子", "和瑞基因",
            "微医", "春雨医生", "好大夫在线", "丁香园", "平安好医生", "阿里健康", "京东健康", "1药网",
            
            // 电商/零售
            "拼多多", "唯品会", "苏宁易购", "国美在线", "当当网", "聚美优品", "蘑菇街", "美丽说",
            "小红书", "得物", "闲鱼", "转转", "瓜子二手车", "人人车", "优信", "大搜车",
            
            // 企业服务/SaaS
            "钉钉", "企业微信", "飞书", "腾讯会议", "华为云WeLink", "金山办公", "用友网络", "金蝶国际",
            "明源云", "广联达", "东软集团", "中软国际", "文思海辉", "软通动力", "博彦科技", "海辉软件",
            
            // 物流/供应链
            "顺丰速运", "京东物流", "菜鸟网络", "中通快递", "圆通速递", "申通快递", "韵达速递", "百世快递",
            "德邦快递", "安能物流", "壹米滴答", "货拉拉", "满帮集团", "G7", "路歌", "福佑卡车",
            
            // 房地产/建筑
            "万科集团", "恒大集团", "碧桂园", "融创中国", "保利发展", "中海地产", "华润置地", "龙湖集团",
            "绿地控股", "华夏幸福", "招商蛇口", "金地集团", "世茂集团", "绿城中国", "雅居乐", "富力地产",
            
            // 新能源/环保
            "宁德时代", "比亚迪", "国轩高科", "亿纬锂能", "欣旺达", "孚能科技", "中航锂电", "蜂巢能源",
            "远景能源", "金风科技", "明阳智能", "阳光电源", "隆基股份", "通威股份", "晶澳科技", "天合光能"
        };
        
        String[] positions = {
            // AI/算法相关
            "AI Agent开发工程师", "机器学习工程师", "深度学习工程师", "算法工程师", "AI工程师", "计算机视觉工程师",
            "自然语言处理工程师", "推荐算法工程师", "数据挖掘工程师", "AI产品经理", "AI架构师", "智能驾驶算法工程师",
            
            // 后端开发
            "Java开发工程师", "Python开发工程师", "Go开发工程师", "C++开发工程师", "C#开发工程师", "PHP开发工程师",
            "Node.js开发工程师", "Ruby开发工程师", "Scala开发工程师", "Rust开发工程师", "Kotlin开发工程师",
            "微服务架构师", "分布式系统工程师", "高并发开发工程师", "中间件开发工程师", "API开发工程师",
            
            // 前端开发
            "前端开发工程师", "Vue.js开发工程师", "React开发工程师", "Angular开发工程师", "小程序开发工程师",
            "H5开发工程师", "移动端开发工程师", "前端架构师", "全栈开发工程师", "WebGL开发工程师",
            
            // 移动开发
            "Android开发工程师", "iOS开发工程师", "Flutter开发工程师", "React Native开发工程师", "Xamarin开发工程师",
            "移动端架构师", "跨平台开发工程师", "移动端测试工程师", "移动端产品经理",
            
            // 数据相关
            "数据分析师", "数据科学家", "数据工程师", "数据架构师", "大数据开发工程师", "ETL工程师",
            "数据仓库工程师", "实时计算工程师", "数据产品经理", "数据运营", "商业智能分析师",
            
            // 测试相关
            "测试工程师", "自动化测试工程师", "性能测试工程师", "安全测试工程师", "测试开发工程师",
            "移动端测试工程师", "接口测试工程师", "UI自动化测试工程师", "测试架构师", "质量保证工程师",
            
            // 运维/DevOps
            "运维工程师", "DevOps工程师", "云计算工程师", "网络工程师", "系统管理员", "DBA",
            "容器化工程师", "Kubernetes工程师", "监控工程师", "安全工程师", "基础设施工程师",
            "SRE工程师", "云架构师", "运维开发工程师", "自动化运维工程师",
            
            // 产品/设计
            "产品经理", "产品总监", "产品运营", "用户研究员", "交互设计师", "UI设计师", "UE设计师",
            "视觉设计师", "平面设计师", "品牌设计师", "动效设计师", "游戏美术设计师", "产品设计师",
            "用户体验设计师", "用户界面设计师", "工业设计师", "空间设计师",
            
            // 管理岗位
            "项目经理", "技术经理", "研发经理", "部门总监", "CTO", "VP", "CEO", "COO", "CFO",
            "技术总监", "产品总监", "运营总监", "市场总监", "销售总监", "人事总监", "财务总监",
            
            // 销售/市场
            "市场营销", "销售经理", "客户经理", "商务拓展", "渠道经理", "品牌经理", "市场推广",
            "数字营销", "内容运营", "社群运营", "活动策划", "公关经理", "媒体运营", "SEO/SEM",
            
            // 人力资源
            "人力资源", "招聘专员", "培训师", "薪酬福利", "员工关系", "HRBP", "组织发展",
            "人才发展", "绩效管理", "企业文化", "HRIS", "人力资源总监", "招聘经理",
            
            // 财务/法务
            "财务分析", "会计", "出纳", "税务专员", "审计", "风控专员", "投资经理", "财务经理",
            "成本会计", "管理会计", "财务总监", "CFO", "法务专员", "知识产权", "合规专员",
            "律师", "法律顾问", "商务合同", "法务经理", "合规经理",
            
            // 游戏开发
            "游戏开发工程师", "游戏策划", "游戏美术", "游戏程序", "游戏测试", "游戏运营",
            "Unity开发工程师", "Unreal开发工程师", "游戏服务器开发", "游戏客户端开发",
            
            // 区块链/Web3
            "区块链开发工程师", "智能合约工程师", "DeFi开发工程师", "NFT开发工程师", "Web3产品经理",
            "区块链架构师", "加密货币分析师", "区块链安全工程师",
            
            // 硬件/嵌入式
            "嵌入式开发工程师", "硬件工程师", "FPGA工程师", "芯片设计工程师", "物联网工程师",
            "智能硬件工程师", "机器人工程师", "自动驾驶工程师", "无人机工程师",
            
            // 咨询/服务
            "技术咨询", "业务咨询", "管理咨询", "IT咨询", "数字化转型顾问", "解决方案架构师",
            "售前工程师", "售后工程师", "技术支持", "客户成功经理", "实施顾问"
        };
        
        String[] cities = {
            // 一线城市
            "北京", "上海", "深圳", "广州",
            
            // 新一线城市
            "杭州", "成都", "武汉", "西安", "南京", "苏州", "天津", "重庆", "青岛", "大连",
            "厦门", "宁波", "无锡", "佛山", "东莞", "合肥", "郑州", "长沙", "济南", "哈尔滨",
            "福州", "昆明", "石家庄", "南昌", "贵阳", "南宁", "太原", "兰州", "海口", "银川",
            "西宁", "乌鲁木齐", "拉萨", "呼和浩特", "长春", "沈阳",
            
            // 二线城市
            "温州", "嘉兴", "金华", "台州", "绍兴", "湖州", "衢州", "丽水", "舟山",
            "绵阳", "德阳", "南充", "宜宾", "自贡", "乐山", "泸州", "达州", "内江", "遂宁",
            "襄阳", "宜昌", "荆州", "黄石", "十堰", "荆门", "鄂州", "孝感", "黄冈", "咸宁",
            "宝鸡", "咸阳", "渭南", "汉中", "安康", "商洛", "延安", "榆林", "铜川",
            "徐州", "常州", "南通", "连云港", "淮安", "盐城", "扬州", "镇江", "泰州", "宿迁",
            "昆山", "江阴", "张家港", "常熟", "太仓", "宜兴", "溧阳", "金坛", "丹阳", "扬中",
            
            // 三线城市
            "保定", "唐山", "秦皇岛", "邯郸", "邢台", "沧州", "廊坊", "衡水", "承德", "张家口",
            "芜湖", "蚌埠", "淮南", "马鞍山", "淮北", "铜陵", "安庆", "黄山", "滁州", "阜阳",
            "宿州", "六安", "亳州", "池州", "宣城", "界首", "明光", "天长", "桐城", "宁国",
            "株洲", "湘潭", "衡阳", "邵阳", "岳阳", "常德", "张家界", "益阳", "郴州", "永州",
            "怀化", "娄底", "吉首", "醴陵", "湘乡", "耒阳", "常宁", "浏阳", "津市", "沅江",
            
            // 海外城市
            "香港", "澳门", "台北", "高雄", "新加坡", "东京", "首尔", "曼谷", "吉隆坡", "雅加达",
            "马尼拉", "胡志明市", "河内", "金边", "万象", "仰光", "达卡", "科伦坡", "加德满都",
            "纽约", "洛杉矶", "旧金山", "西雅图", "波士顿", "芝加哥", "华盛顿", "迈阿密", "亚特兰大", "达拉斯",
            "伦敦", "巴黎", "柏林", "慕尼黑", "阿姆斯特丹", "苏黎世", "维也纳", "布鲁塞尔", "哥本哈根", "斯德哥尔摩",
            "悉尼", "墨尔本", "布里斯班", "珀斯", "阿德莱德", "奥克兰", "惠灵顿", "多伦多", "温哥华", "蒙特利尔"
        };
        
        String[] categories = {"社招", "校招", "实习"};
        
        String[] industries = {
            // 互联网/科技
            "互联网", "人工智能", "大数据", "云计算", "区块链", "物联网", "企业服务", "SaaS", "PaaS", "IaaS",
            "移动互联网", "社交网络", "内容平台", "工具软件", "开发者工具", "API服务", "中间件", "开源软件",
            
            // 金融/支付
            "金融科技", "支付", "银行", "保险", "证券", "基金", "期货", "外汇", "数字货币", "P2P",
            "消费金融", "供应链金融", "财富管理", "资产管理", "风险管理", "征信", "反欺诈", "量化交易",
            
            // 电商/零售
            "电商", "零售", "新零售", "跨境电商", "社交电商", "直播电商", "社区团购", "生鲜电商",
            "奢侈品", "美妆", "服装", "3C数码", "家电", "家居", "母婴", "食品饮料", "图书音像",
            
            // 游戏/娱乐
            "游戏", "手游", "端游", "页游", "VR游戏", "AR游戏", "电竞", "直播", "短视频", "长视频",
            "音乐", "音频", "阅读", "动漫", "二次元", "IP运营", "内容创作", "MCN", "网红经济",
            
            // 教育/培训
            "在线教育", "K12教育", "职业教育", "成人教育", "语言培训", "技能培训", "企业培训", "考试培训",
            "早教", "素质教育", "艺术培训", "体育培训", "留学服务", "教育信息化", "教育硬件", "教育内容",
            
            // 医疗/健康
            "医疗健康", "互联网医疗", "医疗器械", "生物医药", "基因检测", "精准医疗", "数字医疗", "远程医疗",
            "健康管理", "运动健身", "美容护肤", "医美", "心理健康", "养老", "康复", "体检", "保险",
            
            // 汽车/交通
            "汽车", "新能源汽车", "智能汽车", "自动驾驶", "车联网", "共享出行", "网约车", "出租车",
            "物流运输", "货运", "快递", "仓储", "供应链", "冷链", "跨境物流", "同城配送", "最后一公里",
            
            // 房地产/建筑
            "房地产", "建筑", "装修", "家居", "物业管理", "商业地产", "产业地产", "文旅地产", "养老地产",
            "建筑设计", "工程咨询", "建筑材料", "智能家居", "智慧社区", "城市更新", "特色小镇",
            
            // 制造业/工业
            "制造业", "工业4.0", "智能制造", "工业互联网", "机器人", "自动化", "3D打印", "新材料",
            "新能源", "光伏", "风电", "储能", "电池", "充电桩", "氢能", "核能", "环保", "节能",
            
            // 农业/食品
            "农业", "智慧农业", "农业科技", "食品饮料", "餐饮", "外卖", "生鲜", "农产品", "农业金融",
            "农业保险", "农业物流", "农业电商", "农业大数据", "精准农业", "有机农业", "农业旅游",
            
            // 旅游/酒店
            "旅游", "酒店", "民宿", "度假村", "景区", "旅行社", "在线旅游", "商务旅行", "定制旅游",
            "旅游科技", "旅游金融", "旅游保险", "旅游地产", "会展", "活动策划", "文化创意",
            
            // 媒体/广告
            "媒体", "广告", "营销", "公关", "品牌", "数字营销", "内容营销", "社交媒体", "搜索引擎",
            "程序化广告", "广告技术", "营销自动化", "客户关系管理", "销售管理", "渠道管理",
            
            // 咨询/服务
            "管理咨询", "IT咨询", "财务咨询", "法律咨询", "人力资源", "猎头", "外包服务", "专业服务",
            "知识产权", "专利", "商标", "版权", "技术转移", "创业服务", "孵化器", "加速器",
            
            // 政府/非营利
            "政府", "公共事业", "非营利组织", "慈善", "基金会", "行业协会", "研究机构", "智库",
            "国际组织", "NGO", "社会企业", "公益", "环保", "可持续发展", "社会责任"
        };
        
        String[] companyScales = {"20-99人", "100-499人", "500-999人", "1000-9999人", "10000人以上"};
        
        for (int i = 1; i <= 2000; i++) {

            
            
            Map<String, Object> job = new HashMap<>();
            
            // 基本信息
            job.put("jobId", "JOB_" + String.format("%02d", i));
            job.put("title", positions[i % positions.length]);
            job.put("company", companies[i % companies.length]);
            job.put("city", cities[i % cities.length]);
            job.put("category", categories[i % categories.length]);
            job.put("industry", industries[i % industries.length]);
            job.put("companyScale", companyScales[i % companyScales.length]);
            
            // 薪资和经验
            String category = (String) job.get("category");
            job.put("salary", generateRandomSalary(category));
            job.put("experience", generateRandomExperience(category));
            job.put("education", generateRandomEducation());
            
            // 确保前几个职位包含"AI Agent开发工程师"和"校园招聘"类别（测试同义词匹配）
            if (i <= 3) {
                job.put("title", "AI Agent开发工程师");
                job.put("category", "校园招聘");
                job.put("city", "上海");
                job.put("salary", "15k-30k");
            }
            
            // 时间信息
            job.put("publishTime", LocalDateTime.now().minusDays(i % 30).minusHours(i % 24));
            job.put("updateTime", LocalDateTime.now().minusDays(i % 7));
            
            // 职位状态
            job.put("status", i % 100 == 0 ? "已下线" : "招聘中");
            job.put("urgent", i % 50 == 0);
            job.put("viewCount", (i * 7) % 1000);
            job.put("applyCount", (i * 3) % 200);
            
            // 职责描述
            job.put("responsibilities", generateResponsibilities((String) job.get("title")));
            
            // 岗位要求
            job.put("requirements", generateRequirements(category, (String) job.get("title")));
            
            // 公司信息
            job.put("companyInfo", generateCompanyInfo((String) job.get("company"), (String) job.get("industry")));
            
            jobs.add(job);
            
            // 每生成5个职位打印一次进度
            if (i % 5 == 0) {
                log.info("已生成 {} 个职位", i);
            }
        }
        
        cachedJobs = jobs;
        log.info("职位数据生成完成，共 {} 个职位", jobs.size());
        return jobs;
    }
    
    /**
     * 根据搜索条件过滤职位 - 增强版本
     */
    private List<Map<String, Object>> filterJobs(List<Map<String, Object>> allJobs, 
                                                String city, String jobTitle, String salaryRange, String jobCategory,
                                                String experience, String education, String companyScale, 
                                                String industry, String keywords) {
        log.info("开始过滤职位，搜索条件: city={}, jobTitle={}, salaryRange={}, jobCategory={}, experience={}, education={}, companyScale={}, industry={}, keywords={}", 
                city, jobTitle, salaryRange, jobCategory, experience, education, companyScale, industry, keywords);
        
        List<Map<String, Object>> filtered = allJobs.stream()
                .filter(job -> {
                    boolean cityMatch = city == null || city.equals(job.get("city"));
                    if (!cityMatch) {
                        log.debug("城市不匹配: 期望={}, 实际={}", city, job.get("city"));
                    }
                    return cityMatch;
                })
                .filter(job -> {
                    boolean titleMatch = jobTitle == null || ((String) job.get("title")).contains(jobTitle);
                    if (!titleMatch) {
                        log.debug("职位名称不匹配: 期望包含={}, 实际={}", jobTitle, job.get("title"));
                    }
                    return titleMatch;
                })
                .filter(job -> {
                    boolean categoryMatch = matchesCategory((String) job.get("category"), jobCategory);
                    if (!categoryMatch) {
                        log.debug("职位类别不匹配: 期望={}, 实际={}", jobCategory, job.get("category"));
                    }
                    return categoryMatch;
                })
                .filter(job -> {
                    boolean salaryMatch = salaryRange == null || matchesSalaryRange((String) job.get("salary"), salaryRange);
                    if (!salaryMatch) {
                        log.debug("薪资不匹配: 期望={}, 实际={}", salaryRange, job.get("salary"));
                    }
                    return salaryMatch;
                })
                .filter(job -> {
                    boolean experienceMatch = matchesExperienceRobust((String) job.get("experience"), experience);
                    if (!experienceMatch) {
                        log.debug("经验要求不匹配: 期望={}, 实际={}", experience, job.get("experience"));
                    }
                    return experienceMatch;
                })
                .filter(job -> {
                    boolean educationMatch = education == null || matchesEducation((String) job.get("education"), education);
                    if (!educationMatch) {
                        log.debug("学历要求不匹配: 期望={}, 实际={}", education, job.get("education"));
                    }
                    return educationMatch;
                })
                .filter(job -> {
                    boolean scaleMatch = companyScale == null || companyScale.equals(job.get("companyScale"));
                    if (!scaleMatch) {
                        log.debug("公司规模不匹配: 期望={}, 实际={}", companyScale, job.get("companyScale"));
                    }
                    return scaleMatch;
                })
                .filter(job -> {
                    boolean industryMatch = industry == null || industry.equals(job.get("industry"));
                    if (!industryMatch) {
                        log.debug("行业不匹配: 期望={}, 实际={}", industry, job.get("industry"));
                    }
                    return industryMatch;
                })
                .filter(job -> {
                    boolean keywordsMatch = keywords == null || matchesKeywords(job, keywords);
                    if (!keywordsMatch) {
                        log.debug("关键词不匹配: 期望包含={}", keywords);
                    }
                    return keywordsMatch;
                })
                .filter(job -> {
                    boolean statusMatch = "招聘中".equals(job.get("status"));
                    if (!statusMatch) {
                        log.debug("职位状态不匹配: 期望=招聘中, 实际={}", job.get("status"));
                    }
                    return statusMatch;
                })
                .collect(ArrayList::new, (list, job) -> list.add(job), ArrayList::addAll);
        
        log.info("过滤完成，匹配到 {} 个职位", filtered.size());
        return filtered;
    }

    /**
     * 生成模拟投递结果
     */
    private Map<String, Object> generateRealisticApplicationResult(String jobId, String resumeInfo, String coverLetter, 
                                                                  Map<String, Object> targetJob, String userId) {
        Map<String, Object> result = new HashMap<>();
        
        // 生成投递ID
        String applicationId = "APPLY_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        result.put("applicationId", applicationId);
        result.put("jobId", jobId);
        result.put("userId", userId != null ? userId : "USER_" + System.currentTimeMillis());
        
        // 投递状态和时间
        result.put("status", "已投递");
        result.put("applyTime", LocalDateTime.now());
        result.put("updateTime", LocalDateTime.now());
        
        // 职位信息
        result.put("jobInfo", Map.of(
            "title", targetJob.get("title"),
            "company", targetJob.get("company"),
            "city", targetJob.get("city"),
            "salary", targetJob.get("salary")
        ));
        
        // 投递内容摘要
        result.put("applicationSummary", Map.of(
            "resumeLength", resumeInfo != null ? resumeInfo.length() : 0,
            "coverLetterLength", coverLetter != null ? coverLetter.length() : 0,
            "hasCoverLetter", coverLetter != null && !coverLetter.trim().isEmpty()
        ));
        
        // 处理状态和预计时间
        String currentStatus = "已投递";
        result.put("currentStatus", currentStatus);
        result.put("nextStatus", "HR查看中");
        
        // 预计处理时间
        LocalDateTime estimatedViewTime = LocalDateTime.now().plusHours(2 + (int)(Math.random() * 24));
        LocalDateTime estimatedResponseTime = LocalDateTime.now().plusDays(1 + (int)(Math.random() * 5));
        result.put("estimatedViewTime", estimatedViewTime);
        result.put("estimatedResponseTime", estimatedResponseTime);
        
        // 投递统计
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalApplications", 1 + (int)(Math.random() * 10));
        statistics.put("successRate", (70 + (int)(Math.random() * 30)) + "%");
        statistics.put("averageResponseTime", (1 + (int)(Math.random() * 4)) + "天");
        statistics.put("interviewRate", (30 + (int)(Math.random() * 40)) + "%");
        statistics.put("offerRate", (10 + (int)(Math.random() * 20)) + "%");
        result.put("statistics", statistics);
        
        // 投递建议
        List<String> suggestions = new ArrayList<>();
        suggestions.add("保持手机畅通，注意接听面试电话");
        suggestions.add("准备好自我介绍和项目经验介绍");
        suggestions.add("了解公司背景和业务方向");
        suggestions.add("3天后如无回复，可主动联系HR");
        suggestions.add("完善简历，突出与岗位匹配的技能");
        suggestions.add("准备技术面试相关问题");
        result.put("suggestions", suggestions);
        
        // 后续跟进计划
        Map<String, Object> followUp = new HashMap<>();
        followUp.put("nextAction", "等待HR查看");
        followUp.put("actionTime", "1-3个工作日");
        followUp.put("reminderTime", LocalDateTime.now().plusDays(3));
        followUp.put("canWithdraw", true);
        followUp.put("withdrawDeadline", LocalDateTime.now().plusDays(1));
        result.put("followUp", followUp);
        
        // 投递成功消息
        result.put("message", String.format("""
            📤 投递成功！
            
            **投递信息**:
            - 投递编号: %s
            - 职位: %s
            - 公司: %s
            - 投递时间: %s
            
            **处理进度**:
            - 当前状态: %s
            - 预计查看时间: %s
            - 预计回复时间: %s
            
            **温馨提示**:
            ✅ 投递成功，请耐心等待HR查看
            📱 保持联系方式畅通
            📚 继续完善技术能力
            🎯 可以继续投递其他心仪岗位
            
            **下一步**: 等待HR查看，预计1-3个工作日内有回复
            """, 
            applicationId,
            targetJob.get("title"),
            targetJob.get("company"),
            LocalDateTime.now().toString(),
            currentStatus,
            estimatedViewTime.toString(),
            estimatedResponseTime.toString()
        ));
        
        return result;
    }

    /**
     * 生成模拟职位详情
     */
    private Map<String, Object> generateMockJobDetail(String jobId) {
        Map<String, Object> job = new HashMap<>();
        
        job.put("jobId", jobId);
        job.put("title", "高级Java开发工程师");
        job.put("company", "北京科技有限公司");
        job.put("city", "北京");
        job.put("category", "社招");
        job.put("salary", "20-30K·13薪");
        job.put("experience", "3-5年");
        job.put("education", "本科及以上");
        job.put("publishTime", LocalDateTime.now().minusDays(2));
        
        job.put("responsibilities", Arrays.asList(
            "负责后端系统架构设计和开发，确保系统稳定性和可扩展性",
            "参与需求分析和技术方案设计，制定开发计划",
            "编写高质量、可维护的代码，遵循编码规范",
            "参与代码审查和技术分享，提升团队技术水平",
            "协助团队解决技术难题，优化系统性能"
        ));
        
        job.put("requirements", Arrays.asList(
            "本科及以上学历，计算机相关专业",
            "3-5年Java开发经验，熟练掌握Java核心技术",
            "精通Spring Boot、Spring MVC等主流框架",
            "熟悉MySQL、Redis等数据库技术",
            "具备良好的系统设计和架构能力",
            "有大型项目开发经验，了解微服务架构",
            "具备良好的团队协作和沟通能力"
        ));
        
        job.put("companyInfo", Map.of(
            "name", "北京科技有限公司",
            "scale", "500-1000人",
            "industry", "互联网/软件",
            "financing", "B轮",
            "location", "北京市朝阳区"
        ));
        
        return job;
    }

    /**
     * 生成模拟公司信息 - 更真实详细
     */
    private Map<String, Object> generateMockCompanyInfo(String companyName) {
        Map<String, Object> companyInfo = new HashMap<>();
        
        // 根据公司名称生成不同的信息
        if (companyName.contains("百度")) {
            companyInfo.put("scale", "10000人以上");
            companyInfo.put("industry", "人工智能/搜索引擎");
            companyInfo.put("stage", "已上市");
            companyInfo.put("foundedYear", "2000年");
            companyInfo.put("location", "北京市海淀区中关村软件园");
            companyInfo.put("companyType", "上市公司");
            companyInfo.put("website", "www.baidu.com");
            companyInfo.put("description", "百度是全球最大的中文搜索引擎，致力于让人们更便捷地获取信息，找到所求。百度在人工智能、自动驾驶、云计算等领域处于领先地位，拥有Apollo自动驾驶平台、飞桨深度学习框架等核心技术。");
            companyInfo.put("businessScope", Arrays.asList(
                "搜索引擎服务",
                "人工智能技术",
                "自动驾驶",
                "云计算服务",
                "移动互联网应用",
                "智能硬件",
                "企业服务"
            ));
            companyInfo.put("coreProducts", Arrays.asList("百度搜索", "百度地图", "百度网盘", "小度", "Apollo", "飞桨"));
            companyInfo.put("companyCulture", "简单可依赖");
            companyInfo.put("annualRevenue", "1000亿+");
        } else if (companyName.contains("腾讯")) {
            companyInfo.put("scale", "10000人以上");
            companyInfo.put("industry", "互联网/游戏/社交");
            companyInfo.put("stage", "已上市");
            companyInfo.put("foundedYear", "1998年");
            companyInfo.put("location", "深圳市南山区科技园");
            companyInfo.put("companyType", "上市公司");
            companyInfo.put("website", "www.tencent.com");
            companyInfo.put("description", "腾讯是中国领先的互联网增值服务提供商之一，通过互联网服务提升人类生活品质。腾讯在社交、游戏、金融科技、云计算等领域具有重要影响力，拥有微信、QQ、王者荣耀等知名产品。");
            companyInfo.put("businessScope", Arrays.asList(
                "社交网络服务",
                "网络游戏",
                "金融科技",
                "云计算服务",
                "数字内容",
                "企业服务",
                "广告服务"
            ));
            companyInfo.put("coreProducts", Arrays.asList("微信", "QQ", "王者荣耀", "和平精英", "腾讯云", "企业微信"));
            companyInfo.put("companyCulture", "正直、进取、协作、创造");
            companyInfo.put("annualRevenue", "5000亿+");
        } else if (companyName.contains("字节跳动")) {
            companyInfo.put("scale", "10000人以上");
            companyInfo.put("industry", "互联网/内容/短视频");
            companyInfo.put("stage", "未上市");
            companyInfo.put("foundedYear", "2012年");
            companyInfo.put("location", "北京市海淀区知春路");
            companyInfo.put("companyType", "民营企业");
            companyInfo.put("website", "www.bytedance.com");
            companyInfo.put("description", "字节跳动是一家全球化的互联网技术公司，致力于用技术丰富人们的生活。旗下产品包括抖音、今日头条、TikTok等，在全球拥有数十亿用户，是短视频和内容推荐领域的领导者。");
            companyInfo.put("businessScope", Arrays.asList(
                "短视频平台",
                "信息流产品",
                "企业服务",
                "教育科技",
                "游戏业务",
                "电商业务",
                "广告服务"
            ));
            companyInfo.put("coreProducts", Arrays.asList("抖音", "今日头条", "TikTok", "西瓜视频", "懂车帝", "飞书"));
            companyInfo.put("companyCulture", "始终创业、坦诚清晰、追求极致、务实敢为");
            companyInfo.put("annualRevenue", "3000亿+");
        } else if (companyName.contains("阿里巴巴")) {
            companyInfo.put("scale", "10000人以上");
            companyInfo.put("industry", "电商/云计算/金融");
            companyInfo.put("stage", "已上市");
            companyInfo.put("foundedYear", "1999年");
            companyInfo.put("location", "杭州市余杭区文一西路");
            companyInfo.put("companyType", "上市公司");
            companyInfo.put("website", "www.alibaba.com");
            companyInfo.put("description", "阿里巴巴集团是以曾担任英语教师的马云为首的18人于1999年在浙江杭州创立的公司。阿里巴巴集团经营多项业务，另外也从关联公司的业务和服务中取得经营商业生态系统上的支援。");
            companyInfo.put("businessScope", Arrays.asList(
                "电子商务",
                "云计算服务",
                "数字媒体",
                "金融科技",
                "物流服务",
                "企业服务",
                "新零售"
            ));
            companyInfo.put("coreProducts", Arrays.asList("淘宝", "天猫", "支付宝", "阿里云", "菜鸟", "钉钉"));
            companyInfo.put("companyCulture", "客户第一、员工第二、股东第三");
            companyInfo.put("annualRevenue", "7000亿+");
        } else if (companyName.contains("华为")) {
            companyInfo.put("scale", "10000人以上");
            companyInfo.put("industry", "通信设备/消费电子");
            companyInfo.put("stage", "未上市");
            companyInfo.put("foundedYear", "1987年");
            companyInfo.put("location", "深圳市龙岗区坂田华为基地");
            companyInfo.put("companyType", "民营企业");
            companyInfo.put("website", "www.huawei.com");
            companyInfo.put("description", "华为技术有限公司是一家生产销售通信设备的民营通信科技公司，于1987年正式注册成立，总部位于中国深圳市龙岗区坂田华为基地。华为是全球领先的信息与通信技术（ICT）解决方案供应商。");
            companyInfo.put("businessScope", Arrays.asList(
                "通信设备",
                "消费电子",
                "企业服务",
                "云计算",
                "人工智能",
                "智能汽车",
                "芯片设计"
            ));
            companyInfo.put("coreProducts", Arrays.asList("华为手机", "华为云", "鸿蒙系统", "5G设备", "智能汽车", "昇腾芯片"));
            companyInfo.put("companyCulture", "以客户为中心，以奋斗者为本，长期艰苦奋斗");
            companyInfo.put("annualRevenue", "6000亿+");
        } else if (companyName.contains("小米")) {
            companyInfo.put("scale", "10000人以上");
            companyInfo.put("industry", "消费电子/智能硬件");
            companyInfo.put("stage", "已上市");
            companyInfo.put("foundedYear", "2010年");
            companyInfo.put("location", "北京市海淀区清河中街");
            companyInfo.put("companyType", "上市公司");
            companyInfo.put("website", "www.mi.com");
            companyInfo.put("description", "小米科技有限责任公司成立于2010年4月，是一家以手机、智能硬件和IoT平台为核心的互联网公司。小米坚持'为发烧而生'的产品理念，致力于让全球每个人都能享受科技带来的美好生活。");
            companyInfo.put("businessScope", Arrays.asList(
                "智能手机",
                "智能硬件",
                "IoT平台",
                "互联网服务",
                "新零售",
                "智能汽车",
                "生态链投资"
            ));
            companyInfo.put("coreProducts", Arrays.asList("小米手机", "MIUI", "小米生态链", "小米汽车", "小米云", "小米有品"));
            companyInfo.put("companyCulture", "为发烧而生");
            companyInfo.put("annualRevenue", "2000亿+");
        } else if (companyName.contains("美团")) {
            companyInfo.put("scale", "10000人以上");
            companyInfo.put("industry", "本地生活服务/外卖");
            companyInfo.put("stage", "已上市");
            companyInfo.put("foundedYear", "2010年");
            companyInfo.put("location", "北京市朝阳区望京东路");
            companyInfo.put("companyType", "上市公司");
            companyInfo.put("website", "www.meituan.com");
            companyInfo.put("description", "美团是一家中国生活服务电子商务平台，公司名称为北京三快在线科技有限公司。美团网有着'吃喝玩乐全都有'和'美团一次美一次'的服务宣传宗旨。");
            companyInfo.put("businessScope", Arrays.asList(
                "外卖配送",
                "到店餐饮",
                "酒店旅游",
                "出行服务",
                "新零售",
                "企业服务",
                "金融服务"
            ));
            companyInfo.put("coreProducts", Arrays.asList("美团外卖", "美团", "大众点评", "美团单车", "美团优选", "美团买菜"));
            companyInfo.put("companyCulture", "以客户为中心，长期有耐心");
            companyInfo.put("annualRevenue", "1500亿+");
        } else if (companyName.contains("滴滴")) {
            companyInfo.put("scale", "10000人以上");
            companyInfo.put("industry", "出行服务/共享经济");
            companyInfo.put("stage", "未上市");
            companyInfo.put("foundedYear", "2012年");
            companyInfo.put("location", "北京市海淀区中关村");
            companyInfo.put("companyType", "民营企业");
            companyInfo.put("website", "www.didiglobal.com");
            companyInfo.put("description", "滴滴出行是全球领先的一站式多元化出行平台，在中国400余座城市为近5亿用户提供出租车召车、专车、快车、顺风车、代驾、试驾、巴士和企业级等全面出行服务。");
            companyInfo.put("businessScope", Arrays.asList(
                "网约车服务",
                "出租车服务",
                "顺风车服务",
                "代驾服务",
                "货运服务",
                "自动驾驶",
                "金融服务"
            ));
            companyInfo.put("coreProducts", Arrays.asList("滴滴出行", "滴滴货运", "滴滴代驾", "滴滴企业版", "青桔单车", "自动驾驶"));
            companyInfo.put("companyCulture", "让出行更美好");
            companyInfo.put("annualRevenue", "1000亿+");
        } else {
            // 根据公司名称智能生成默认信息
            generateDefaultCompanyInfo(companyInfo, companyName);
        }
        
        // 通用福利待遇
        companyInfo.put("benefits", generateCompanyBenefits(companyName));
        
        // 添加更多详细信息
        companyInfo.put("workEnvironment", generateWorkEnvironment(companyName));
        companyInfo.put("developmentOpportunities", generateDevelopmentOpportunities(companyName));
        companyInfo.put("companyHighlights", generateCompanyHighlights(companyName));
        
        return companyInfo;
    }
    
    /**
     * 生成默认公司信息
     */
    private void generateDefaultCompanyInfo(Map<String, Object> companyInfo, String companyName) {
        String[] scales = {"20-99人", "100-499人", "500-999人", "1000-9999人", "10000人以上"};
        String[] stages = {"未融资", "天使轮", "A轮", "B轮", "C轮", "D轮及以上", "已上市", "不需要融资"};
        String[] industries = {"互联网", "人工智能", "金融科技", "企业服务", "电商", "游戏", "教育", "医疗", "汽车", "新能源"};
        String[] cities = {"北京", "上海", "深圳", "杭州", "广州", "成都", "武汉", "西安", "南京", "苏州"};
        String[] foundedYears = {"2015年", "2016年", "2017年", "2018年", "2019年", "2020年", "2021年", "2022年", "2023年"};
        
        // 根据公司名称哈希值生成稳定的随机选择
        int hash = Math.abs(companyName.hashCode());
        
        companyInfo.put("scale", scales[hash % scales.length]);
        companyInfo.put("industry", industries[hash % industries.length]);
        companyInfo.put("stage", stages[hash % stages.length]);
        companyInfo.put("foundedYear", foundedYears[hash % foundedYears.length]);
        companyInfo.put("location", cities[hash % cities.length] + "市");
        companyInfo.put("companyType", "民营企业");
        companyInfo.put("website", "www." + companyName.toLowerCase().replaceAll("[^a-zA-Z0-9]", "") + ".com");
        companyInfo.put("description", companyName + "是一家专注于" + companyInfo.get("industry") + "领域的创新公司，致力于为用户提供优质的产品和服务，推动行业数字化发展。");
        companyInfo.put("businessScope", Arrays.asList(
            "核心业务开发",
            "技术创新",
            "市场拓展",
            "客户服务",
            "产品运营"
        ));
        companyInfo.put("coreProducts", Arrays.asList("核心产品", "企业服务", "移动应用", "数据分析"));
        companyInfo.put("companyCulture", "创新、协作、专业、进取");
        companyInfo.put("annualRevenue", "1-10亿");
    }
    
    /**
     * 生成公司福利
     */
    private List<String> generateCompanyBenefits(String companyName) {
        List<String> baseBenefits = Arrays.asList(
            "五险一金", "年终奖", "股权激励", "弹性工作", "带薪年假", "免费三餐", "健身房", "团建活动",
            "技术培训", "职业发展通道", "商业保险", "交通补贴", "通讯补贴", "住房补贴", "节日福利",
            "生日福利", "体检", "旅游", "团建", "下午茶", "零食", "咖啡", "茶饮"
        );
        
        // 大公司额外福利
        if (companyName.contains("百度") || companyName.contains("腾讯") || companyName.contains("阿里巴巴") || 
            companyName.contains("字节跳动") || companyName.contains("华为") || companyName.contains("小米")) {
            baseBenefits.addAll(Arrays.asList(
                "股票期权", "无息贷款", "购房补贴", "子女教育", "父母保险", "高端医疗", "海外培训",
                "技术大会", "开源贡献奖励", "专利奖励", "创新奖励", "长期服务奖"
            ));
        }
        
        // 随机选择8-12个福利
        Collections.shuffle(baseBenefits);
        return baseBenefits.subList(0, Math.min(8 + (int)(Math.random() * 5), baseBenefits.size()));
    }
    
    /**
     * 生成工作环境
     */
    private Map<String, Object> generateWorkEnvironment(String companyName) {
        Map<String, Object> environment = new HashMap<>();
        environment.put("officeType", "现代化办公大楼");
        environment.put("workStyle", "弹性工作制");
        environment.put("teamSize", "10-20人团队");
        environment.put("techStack", Arrays.asList("Java", "Python", "React", "Vue.js", "MySQL", "Redis"));
        environment.put("developmentTools", Arrays.asList("Git", "Jenkins", "Docker", "Kubernetes", "AWS", "阿里云"));
        environment.put("workAtmosphere", "开放、创新、协作");
        return environment;
    }
    
    /**
     * 生成发展机会
     */
    private List<String> generateDevelopmentOpportunities(String companyName) {
        return Arrays.asList(
            "技术能力提升",
            "管理经验积累",
            "跨部门协作",
            "行业知识拓展",
            "领导力培养",
            "创新项目参与",
            "海外工作机会",
            "内部转岗机会"
        );
    }
    
    /**
     * 生成公司亮点
     */
    private List<String> generateCompanyHighlights(String companyName) {
        return Arrays.asList(
            "行业领先地位",
            "技术实力雄厚",
            "发展前景广阔",
            "团队氛围良好",
            "薪资福利优厚",
            "成长空间大",
            "工作环境优越",
            "企业文化优秀"
        );
    }

    /**
     * 生成随机薪资
     */
    private String generateRandomSalary(String category) {
        if ("实习".equals(category)) {
            int salary = 150 + (int)(Math.random() * 200); // 150-350元/天
            return salary + "-" + (salary + 50) + "元/天";
        } else if ("校招".equals(category)) {
            int baseSalary = 8 + (int)(Math.random() * 12); // 8-20K
            return baseSalary + "-" + (baseSalary + 5) + "K";
        } else {
            int baseSalary = 10 + (int)(Math.random() * 40); // 10-50K
            return baseSalary + "-" + (baseSalary + 8) + "K";
        }
    }

    /**
     * 生成随机经验要求
     */
    private String generateRandomExperience(String category) {
        if ("实习".equals(category)) {
            return "在校学生";
        } else if ("校招".equals(category)) {
            return "应届毕业生";
        } else {
            String[] experiences = {"1-3年", "3-5年", "5-10年", "10年以上", "不限"};
            return experiences[(int)(Math.random() * experiences.length)];
        }
    }

    /**
     * 生成随机学历要求
     */
    private String generateRandomEducation() {
        String[] educations = {"大专及以上", "本科及以上", "硕士及以上", "博士及以上", "不限"};
        return educations[(int)(Math.random() * educations.length)];
    }

    /**
     * 生成职责描述
     */
    private List<String> generateResponsibilities(String title) {
        List<String> responsibilities = new ArrayList<>();
        
        if (title.contains("开发工程师") || title.contains("程序员")) {
            responsibilities.add("负责" + title.replace("工程师", "") + "相关系统的设计和开发");
            responsibilities.add("参与需求分析和技术方案设计");
            responsibilities.add("编写高质量、可维护的代码");
            responsibilities.add("参与代码审查和技术分享");
            responsibilities.add("协助团队解决技术难题");
            responsibilities.add("参与系统性能优化和监控");
        } else if (title.contains("产品")) {
            responsibilities.add("负责产品需求分析和产品规划");
            responsibilities.add("制定产品发展策略和路线图");
            responsibilities.add("协调各部门资源推进产品开发");
            responsibilities.add("分析用户反馈，持续优化产品体验");
            responsibilities.add("跟踪竞品动态，制定竞争策略");
        } else if (title.contains("设计")) {
            responsibilities.add("负责产品界面设计和用户体验设计");
            responsibilities.add("制定设计规范和设计标准");
            responsibilities.add("与产品经理和开发团队协作");
            responsibilities.add("进行用户研究和可用性测试");
            responsibilities.add("持续优化设计方案");
        } else {
            responsibilities.add("负责" + title + "相关工作的规划和执行");
            responsibilities.add("协调团队资源，推进项目进展");
            responsibilities.add("分析业务数据，提出改进建议");
            responsibilities.add("与各部门保持良好沟通协作");
            responsibilities.add("完成上级安排的其他工作任务");
        }
        
        return responsibilities;
    }

    /**
     * 生成岗位要求
     */
    private List<String> generateRequirements(String category, String title) {
        List<String> requirements = new ArrayList<>();
        
        if ("实习".equals(category)) {
            requirements.add("本科及以上在读，计算机相关专业");
            requirements.add("熟悉基础的编程语言和开发工具");
            requirements.add("学习能力强，有责任心");
            requirements.add("能实习3个月以上");
            requirements.add("有相关项目经验者优先");
        } else if ("校招".equals(category)) {
            requirements.add("2024届毕业生，本科及以上学历");
            requirements.add("计算机、软件工程等相关专业");
            requirements.add("熟悉至少一门编程语言");
            requirements.add("有良好的学习能力和团队协作精神");
            requirements.add("有实习经验或项目经验者优先");
        } else {
            if (title.contains("Java")) {
                requirements.add("本科及以上学历，3年以上Java开发经验");
                requirements.add("精通Spring Boot、Spring MVC等框架");
                requirements.add("熟悉MySQL、Redis等数据库技术");
                requirements.add("了解微服务架构和分布式系统");
                requirements.add("具备良好的代码规范和文档习惯");
            } else if (title.contains("前端")) {
                requirements.add("本科及以上学历，3年以上前端开发经验");
                requirements.add("精通HTML、CSS、JavaScript");
                requirements.add("熟悉Vue、React等前端框架");
                requirements.add("了解Node.js和前端工程化");
                requirements.add("有移动端开发经验者优先");
            } else if (title.contains("产品")) {
                requirements.add("本科及以上学历，3年以上产品经验");
                requirements.add("具备优秀的逻辑思维和分析能力");
                requirements.add("熟悉产品设计流程和方法");
                requirements.add("有数据分析和用户研究经验");
                requirements.add("具备良好的沟通协调能力");
            } else {
                requirements.add("本科及以上学历，相关专业");
                requirements.add("3年以上相关工作经验");
                requirements.add("具备良好的专业技能");
                requirements.add("有团队协作和沟通能力");
                requirements.add("学习能力强，抗压能力好");
            }
        }
        
        return requirements;
    }

    /**
     * 生成公司信息
     */
    private Map<String, Object> generateCompanyInfo(String companyName, String industry) {
        Map<String, Object> companyInfo = new HashMap<>();
        companyInfo.put("name", companyName);
        companyInfo.put("industry", industry);
        
        String[] scales = {"20-99人", "100-499人", "500-999人", "1000-9999人", "10000人以上"};
        companyInfo.put("scale", scales[(int)(Math.random() * scales.length)]);
        
        String[] stages = {"未融资", "天使轮", "A轮", "B轮", "C轮", "D轮及以上", "已上市", "不需要融资"};
        companyInfo.put("stage", stages[(int)(Math.random() * stages.length)]);
        
        String[] benefits = {"五险一金", "年终奖", "股权激励", "弹性工作", "带薪年假", "免费三餐", "健身房", "团建活动"};
        List<String> selectedBenefits = new ArrayList<>();
        for (int i = 0; i < 3 + (int)(Math.random() * 3); i++) {
            selectedBenefits.add(benefits[(int)(Math.random() * benefits.length)]);
        }
        companyInfo.put("benefits", selectedBenefits);
        
        return companyInfo;
    }

    /**
     * 检查薪资是否匹配范围
     */
    private boolean matchesSalaryRange(String jobSalary, String searchRange) {
        // 简化的薪资匹配逻辑
        if (searchRange == null || searchRange.isEmpty()) {
            return true;
        }
        
        log.debug("薪资匹配检查: 职位薪资={}, 搜索范围={}", jobSalary, searchRange);
        
        // 更宽松的薪资匹配逻辑
        // 如果搜索范围是"15k-30k"，职位薪资包含"15"、"20"、"25"、"30"等都算匹配
        String[] rangeParts = searchRange.split("-");
        if (rangeParts.length >= 2) {
            String minSalary = rangeParts[0].replaceAll("[^0-9]", ""); // 提取数字
            String maxSalary = rangeParts[1].replaceAll("[^0-9]", ""); // 提取数字
            
            // 提取职位薪资中的数字
            String jobSalaryNum = jobSalary.replaceAll("[^0-9]", "");
            
            try {
                int min = Integer.parseInt(minSalary);
                int max = Integer.parseInt(maxSalary);
                int jobSalaryInt = Integer.parseInt(jobSalaryNum);
                
                // 如果职位薪资在搜索范围内，或者职位薪资包含搜索范围的最小值
                boolean match = (jobSalaryInt >= min && jobSalaryInt <= max) || 
                               jobSalary.contains(minSalary) || 
                               jobSalary.contains(maxSalary);
                
                log.debug("薪资匹配结果: {} (职位:{}, 范围:{}-{})", match, jobSalaryInt, min, max);
                return match;
            } catch (NumberFormatException e) {
                log.warn("薪资解析失败: jobSalary={}, searchRange={}", jobSalary, searchRange);
                // 如果解析失败，使用简单的包含匹配
                return jobSalary.contains(minSalary) || jobSalary.contains(maxSalary);
            }
        }
        
        // 如果格式不正确，使用简单的包含匹配
        return jobSalary.contains(searchRange.split("-")[0]);
    }


    /**
     * 检查学历要求是否匹配
     */
    private boolean matchesEducation(String jobEducation, String searchEducation) {
        if (searchEducation == null || searchEducation.isEmpty()) {
            return true;
        }
        
        if (searchEducation.contains("不限")) {
            return true;
        }
        
        // 学历等级匹配
        if (searchEducation.contains("大专") && jobEducation.contains("大专")) {
            return true;
        }
        if (searchEducation.contains("本科") && jobEducation.contains("本科")) {
            return true;
        }
        if (searchEducation.contains("硕士") && jobEducation.contains("硕士")) {
            return true;
        }
        if (searchEducation.contains("博士") && jobEducation.contains("博士")) {
            return true;
        }
        
        // 默认包含匹配
        return jobEducation.contains(searchEducation);
    }

    /**
     * 检查关键词是否匹配
     */
    private boolean matchesKeywords(Map<String, Object> job, String keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        
        String[] keywordArray = keywords.split("\\s+");
        String jobText = "";
        
        // 组合职位的所有文本信息
        jobText += job.get("title") + " ";
        jobText += job.get("company") + " ";
        jobText += job.get("industry") + " ";
        
        // 添加职责描述
        if (job.get("responsibilities") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> responsibilities = (List<String>) job.get("responsibilities");
            jobText += String.join(" ", responsibilities) + " ";
        }
        
        // 添加岗位要求
        if (job.get("requirements") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> requirements = (List<String>) job.get("requirements");
            jobText += String.join(" ", requirements) + " ";
        }
        
        jobText = jobText.toLowerCase();
        
        // 检查是否包含所有关键词
        for (String keyword : keywordArray) {
            if (!jobText.contains(keyword.toLowerCase())) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * 对职位进行排序
     */
    private List<Map<String, Object>> sortJobs(List<Map<String, Object>> jobs, String sortBy, String sortOrder) {
        if (jobs == null || jobs.isEmpty()) {
            return jobs;
        }
        
        List<Map<String, Object>> sortedJobs = new ArrayList<>(jobs);
        
        switch (sortBy) {
            case "salary":
                sortedJobs.sort((job1, job2) -> {
                    String salary1 = (String) job1.get("salary");
                    String salary2 = (String) job2.get("salary");
                    int result = compareSalary(salary1, salary2);
                    return "desc".equals(sortOrder) ? -result : result;
                });
                break;
            case "time":
                sortedJobs.sort((job1, job2) -> {
                    LocalDateTime time1 = (LocalDateTime) job1.get("publishTime");
                    LocalDateTime time2 = (LocalDateTime) job2.get("publishTime");
                    int result = time1.compareTo(time2);
                    return "desc".equals(sortOrder) ? -result : result;
                });
                break;
            case "popularity":
                sortedJobs.sort((job1, job2) -> {
                    Integer view1 = (Integer) job1.get("viewCount");
                    Integer view2 = (Integer) job2.get("viewCount");
                    int result = view1.compareTo(view2);
                    return "desc".equals(sortOrder) ? -result : result;
                });
                break;
            default:
                // 默认按时间排序
                sortedJobs.sort((job1, job2) -> {
                    LocalDateTime time1 = (LocalDateTime) job1.get("publishTime");
                    LocalDateTime time2 = (LocalDateTime) job2.get("publishTime");
                    return time2.compareTo(time1); // 默认降序
                });
        }
        
        return sortedJobs;
    }

    /**
     * 比较薪资大小
     */
    private int compareSalary(String salary1, String salary2) {
        try {
            // 提取薪资数字进行比较
            String num1 = salary1.replaceAll("[^0-9]", "");
            String num2 = salary2.replaceAll("[^0-9]", "");
            
            if (num1.isEmpty() || num2.isEmpty()) {
                return 0;
            }
            
            int s1 = Integer.parseInt(num1);
            int s2 = Integer.parseInt(num2);
            
            return Integer.compare(s1, s2);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
