package com.agentfactory.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Value("${af.api-key}")
    private String apiKey;

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter() {
        var registration = new FilterRegistrationBean<>(new ApiKeyFilter(apiKey));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    static class ApiKeyFilter implements Filter {
        private final String expectedKey;

        ApiKeyFilter(String expectedKey) {
            this.expectedKey = expectedKey;
        }

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            var request = (HttpServletRequest) req;
            var response = (HttpServletResponse) res;

            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                chain.doFilter(req, res);
                return;
            }

            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ") && auth.substring(7).equals(expectedKey)) {
                chain.doFilter(req, res);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
            }
        }
    }
}
