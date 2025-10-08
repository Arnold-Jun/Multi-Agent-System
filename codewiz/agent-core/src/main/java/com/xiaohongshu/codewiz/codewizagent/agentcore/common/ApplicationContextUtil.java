package com.xiaohongshu.codewiz.codewizagent.agentcore.common;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

@Service
public class ApplicationContextUtil implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextUtil.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplication() {
        return applicationContext;
    }

    public static <T> T getBean(Class<T> clzType) {
        return applicationContext.getBean(clzType);
    }
}