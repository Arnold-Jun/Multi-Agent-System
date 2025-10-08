package com.xiaohongshu.codewiz.codewizagent.agentcore.auth;

import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.Lists;
import com.xiaohongshu.infra.rpc.base.Context;
import com.xiaohongshu.infra.rpc.ciuser.CiUserRemoteService;
import com.xiaohongshu.infra.rpc.ciuser.RpcBaseUserInfo;
import com.xiaohongshu.infra.rpc.ciuser.RpcBaseUserInfoBlurQuerySearchRequest;
import com.xiaohongshu.infra.rpc.ciuser.RpcBaseUserInfoRequest;
import com.xiaohongshu.infra.rpc.ciuser.RpcBaseUserInfoResponse;
import com.xiaohongshu.infra.rpc.ciuser.RpcGitlabUserInfo;
import com.xiaohongshu.infra.rpc.ciuser.RpcGitlabUserInfoResponse;
import com.xiaohongshu.infra.rpc.ciuser.RpcGitlabUserInfoWithUserIdRequest;
import com.xiaohongshu.infra.rpc.ciuser.RpcSyncUserInfoRequest;
import com.xiaohongshu.infra.rpc.ciuser.RpcSyncUserInfoResponse;
import com.xiaohongshu.infra.rpc.ciuser.RpcSystemAccountBaseUserInfoResponse;
import com.xiaohongshu.infra.rpc.ciuser.RpcSystemAccountQueryRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CiUserRpcClientService {

    @Resource(name = CacheConfig.LONG_FREQUENT_CACHE_NAME)
    private Cache<Object, Object> cache;

    @Resource
    private CiUserRemoteService.Iface ciUser;

    public List<RpcBaseUserInfo> searchExactByEmail(List<String> emailList) {
        List<RpcBaseUserInfo> userInfos = new ArrayList<>();
        Set<String> noCacheList = new HashSet<>(emailList);
        Set<String> cacheKeySet = new HashSet<>(emailList);
        for (String email : cacheKeySet) {
            RpcBaseUserInfo userInfo = (RpcBaseUserInfo) cache.getIfPresent("CiUserRpcClientService#searchExactByEmail#" + email);
            if (userInfo != null) {
                userInfos.add(userInfo);
                noCacheList.remove(email);
            }
        }
        if (CollectionUtils.isNotEmpty(noCacheList)) {
            List<RpcBaseUserInfo> noCacheUserInfoList = searchExactByEmail(new ArrayList<>(noCacheList), null);
            userInfos.addAll(noCacheUserInfoList);
            for (RpcBaseUserInfo userInfo : noCacheUserInfoList) {
                cache.put("CiUserRpcClientService#searchExactByEmail#" + userInfo.getEmail(), userInfo);
            }
        }
        return userInfos;
    }

    public RpcBaseUserInfo searchByEmail(String email) {
        if (StringUtils.isBlank(email)) {
            log.warn("email is blank");
            return null;
        }
        List<RpcBaseUserInfo> rpcBaseUserInfoList = searchExactByEmail(Lists.newArrayList(email));
        if (CollectionUtils.isEmpty(rpcBaseUserInfoList)) {
            log.warn("failed to search email={} base user info", email);
        }
        return CollectionUtils.isEmpty(rpcBaseUserInfoList) ? null : rpcBaseUserInfoList.get(0);
    }

    public List<RpcBaseUserInfo> searchExactByEmail(List<String> emailList, String query) {
        return searchExact(emailList, Lists.newArrayList(), query);
    }

    private List<RpcBaseUserInfo> searchExact(List<String> emailList, List<String> domainList, String query) {
        List<RpcBaseUserInfo> rpcBaseUserInfoList = Lists.newArrayList();
        RpcBaseUserInfoRequest request = new RpcBaseUserInfoRequest();
        if (CollectionUtils.isNotEmpty(emailList)) {
            emailList.removeIf(StringUtils::isBlank);
        }
        if (CollectionUtils.isNotEmpty(domainList)) {
            domainList.removeIf(StringUtils::isBlank);
        }
        if (CollectionUtils.isEmpty(emailList) && CollectionUtils.isEmpty(domainList)) {
            return rpcBaseUserInfoList;
        }
        if (CollectionUtils.isNotEmpty(emailList)) {
            request.setEmailList(emailList);
        } else {
            request.setDomainList(domainList);
        }
        try {
            request.setQuery(query);
            RpcBaseUserInfoResponse response = ciUser.getBaseUserInfo(new Context(), request);
            if (response.isSuccess()) {
                rpcBaseUserInfoList.addAll(response.getData());
            }
        } catch (TException e) {
            log.error("searchExact exception", e);
        }
        return rpcBaseUserInfoList;
    }

    /**
     * 根据gitlab userId获取gitlab用户信息
     *
     * @param userId
     * @return
     */
    public RpcGitlabUserInfo getGitlabInfoWithUserId(long userId) {
        RpcGitlabUserInfoWithUserIdRequest request = new RpcGitlabUserInfoWithUserIdRequest();
        request.setUserId(userId);
        try {
            RpcGitlabUserInfoResponse rpcGitlabUserInfoResponse = ciUser.getGitlabUserInfoByUserId(new Context(), request);
            if (rpcGitlabUserInfoResponse.isSuccess()) {
                RpcGitlabUserInfo rpcGitlabUserInfo = rpcGitlabUserInfoResponse.getData();
                if (rpcGitlabUserInfo == null) {
                    log.warn("failed to search userId={} gitlab account", userId);
                }
                return rpcGitlabUserInfo;
            }
        } catch (Exception e) {
            log.error("getGitlabInfoWithUserId exception", e);
        }
        return null;
    }

    public List<RpcBaseUserInfo> blurSearch(String search) {
        return blurSearch(search, true);
    }

    public RpcBaseUserInfo syncBaseUserOnly(String email) {
        return syncBaseUser(email, 2);
    }

    public RpcBaseUserInfo syncBaseUser(String email, int mode) {
        RpcSyncUserInfoRequest userInfoRequest = new RpcSyncUserInfoRequest();
        userInfoRequest.setEmail(email);
        userInfoRequest.setMode(mode);
        RpcBaseUserInfo rpcBaseUserInfo = null;
        try {
            RpcSyncUserInfoResponse syncUserInfoResponse = ciUser.syncUserInfo(new Context(), userInfoRequest);
            if (!syncUserInfoResponse.isSuccess()) {
                log.warn("failed to sync email={},check whether email is valid", email);
            }
            rpcBaseUserInfo = syncUserInfoResponse.getBaseUserInfo();
        } catch (TException exception) {
            log.error("sync user account exception", exception);
        }
        return rpcBaseUserInfo;
    }

    /**
     * 模糊查询
     *
     * @param search
     * @param onlyPrefixSearch
     * @return
     */
    public List<RpcBaseUserInfo> blurSearch(String search, boolean onlyPrefixSearch) {
        List<RpcBaseUserInfo> rpcBaseUserInfoList = Lists.newArrayList();
        RpcBaseUserInfoBlurQuerySearchRequest request = new RpcBaseUserInfoBlurQuerySearchRequest();
        request.setSearch(search);
        request.setOnlyPrefixSearch(onlyPrefixSearch);
        try {
            RpcBaseUserInfoResponse response = ciUser.getBaseUserInfoForKeyword(new Context(), request);
            if (response.isSuccess()) {
                rpcBaseUserInfoList.addAll(response.getData());
            }
        } catch (TException e) {
            log.error("blurSearch exception", e);
        }
        return rpcBaseUserInfoList;
    }

    public RpcBaseUserInfo getSystemAccountByToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        RpcSystemAccountQueryRequest request = new RpcSystemAccountQueryRequest();
        try {
            request.setToken(token);
            RpcSystemAccountBaseUserInfoResponse response = ciUser.getSystemBaseUserInfo(new Context(), request);
            if (response.isSuccess()) {
                return response.getData();
            }
        } catch (Exception e) {
            log.error("get system account base info exception||req={}", JSON.toJSONString(request), e);
        }
        return null;
    }

    /**
     * 根据署名邮箱前缀查询用户信息
     *
     * @param name 邮箱前缀
     * @return 用户信息列表
     */
    public List<RpcBaseUserInfo> queryUserInfoByRedNamePrefix(String name) {
        RpcBaseUserInfoRequest request = new RpcBaseUserInfoRequest();
        request.setDomainList(Lists.newArrayList(name));
        try {
            RpcBaseUserInfoResponse response = ciUser.getBaseUserInfo(new Context(), request);
            if (!response.isSuccess()) {
                return new ArrayList<>();
            }
            return response.getData();
        } catch (Exception e) {
            log.error("searchExact exception", e);
        }
        return new ArrayList<>();
    }
}
