package com.xiaohongshu.codewiz.codewizagent.cragent.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/27 23:20
 */
public class TodoList {

    @Getter
    private static final TodoList instance = new TodoList();

    private Map<String, String> cache = new ConcurrentHashMap<>();

    private TodoList() {
        cache.put("crAgent", "发起云效CR流程：\n " +
                "1.获取当前仓库信息。\n" +
                "2.检查当前分支工作区是否有未提交改动，如有未提交改动，尝试提交改动到远端\n" +
                "3.获取推荐的目标分支，并检查当前仓库、源分支、目标分支的权限\n" +
                "4.检查当前源分支关联的需求\n" +
                "5.获取推荐审批人信息。\n" +
                "6.检查当前是否存在代码冲突\n" +
                "7.获取cr信息模版\n" +
                "8.创建云效CR" +
                "流程结束。");
//        cache.put("crAgent", "发起云效CR流程：\n " +
//                "1.获取当前仓库信息。\n" +
//                "2.创建云效CR" +
//                "流程结束。");
    }

    public void add(String key, String value) {
        cache.put(key, value);
    }

    public String get(String key) {
        return cache.getOrDefault(key, "");
    }

    public void remove(String key) {
        cache.remove(key);
    }

}

