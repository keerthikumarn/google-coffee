package com.googlecoffee.security;

import com.googlecoffee.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {
    @Bean
    public StaffAuth staffAuth(AppProperties props) {
        return new StaffAuth(props.staffPin(), props.tokenSecret());
    }
}
