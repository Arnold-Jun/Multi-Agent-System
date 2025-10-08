package com.xiaohongshu.codewiz.codewizagent.agentcore.agent;

import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * 临时工具文件
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/27 17:11
 */
@Slf4j
public class ToolService {

//    @Tool("""
//            Get the source branch and repo by Yunxiao.
//            """)
//    public String getSourceBranchAndRepo() {
//        log.info("getSourceBranchAndRepo repo:cibackend, branch:feature/test");
//        return """
//                origin  https://code.devops.sit.xiaohongshu.com/liuyanghong/cibackend.git (fetch)
//                origin  https://code.devops.sit.xiaohongshu.com/liuyanghong/cibackend.git (push)
//                * feature/test
//                """;
//    }

    @Tool("""
            Get the target branch by Yunxiao.
            """)
    public String getTargetBranch() {
        log.info("getTargetBranch targetBranch:release/1.0.0");
        return "release/1.0.0";
    }

    @Tool("""
            Create a MR by Yunxiao.
            """)
    public CreateMrResult createMr(@P("sourceBranch") String sourceBranch,
                                   @P("targetBranch") String targetBranch,
                                   @P("repo") String repo,
                                   @P("title") String title,
                                   @P("approveUser") String approveUser) {
        log.info("createMr sourceBranch:{}, targetBranch:{}, repo:{}, title:{}, approveUser:{}", sourceBranch, targetBranch, repo, title, approveUser);
        CreateMrResult createMrResult = new CreateMrResult();
        createMrResult.setUrl("https://yunxiao.devops.sit.xiaohongshu.com/cr/details?mrId=7&projectId=9187");
        createMrResult.setMrId("7");
        createMrResult.setRepoId("9187");
        log.info("createMr result:{}", JSONObject.toJSON(createMrResult));
        return createMrResult;
    }

    @Tool("""
            Get the approve user by Yunxiao.
            """)
    public String getApproveUser(@P("repo") String repo) {
        log.info("getApproveUser repo:{}, approveUser: liuyanghong@xiaohongshu.com", repo);
        return "liuyanghong@xiaohongshu.com";
    }
}
