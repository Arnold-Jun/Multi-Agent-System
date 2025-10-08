package com.xiaohongshu.codewiz.codewizagent.cragent.common;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * knife4j配置，swagger2配置
 *
 * @author zhoubenjin
 * @date 2022-09-28
 */
@Configuration
public class SwaggerConfig {
	@Bean(value = "dockerBean")
	public Docket dockerBean() {
		//指定使用Swagger2规范
		Docket docket = new Docket(DocumentationType.OAS_30)
				.apiInfo(new ApiInfoBuilder()
						//描述字段支持Markdown语法
						.description("Agent")
						.termsOfServiceUrl("https://ciview.devops.xiaohongshu.com")
						.contact(new Contact("codewiz-agent", "", ""))
						.version("1.0")
						.build())
				//分组名称
				.groupName("codewiz-agent")
				.enable(true)
				.select()
				//这里指定Controller扫描包路径
				.apis(RequestHandlerSelectors.withClassAnnotation(RestController.class))
				.paths(PathSelectors.any())
				.build();
		return docket;
	}
}