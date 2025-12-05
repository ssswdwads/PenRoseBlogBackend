package com.kirisamemarisa.blog.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiConfig {

    @Bean
    public RestTemplate aiRestTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setConnectionRequestTimeout(10_000);
        factory.setReadTimeout(90_000);
        return new RestTemplate(factory);
    }
}
