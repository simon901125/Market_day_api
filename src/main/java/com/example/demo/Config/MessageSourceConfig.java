package com.example.demo.Config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();

        ms.setBasename("classpath:systemMessages/messages");
        ms.setDefaultEncoding("UTF-8");

        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(true);

        return ms;
    }
}
