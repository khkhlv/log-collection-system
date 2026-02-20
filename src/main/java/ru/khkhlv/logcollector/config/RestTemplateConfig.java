package ru.khkhlv.logcollector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * Создаёт RestTemplate bean для HTTP-запросов.
     * Этот bean будет автоматически внедрён в LogGenerator и другие классы.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}