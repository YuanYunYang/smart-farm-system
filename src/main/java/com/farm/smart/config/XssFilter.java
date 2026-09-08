package com.farm.smart.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * XSS 防护过滤器
 * <p>
 * 拦截所有请求参数，过滤潜在的 XSS 攻击脚本。
 *
 * @author Smart Farm Team
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class XssFilter implements Filter {

    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("<script.*?>.*?</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe.*?>", Pattern.CASE_INSENSITIVE),
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(new XssRequestWrapper((HttpServletRequest) request), response);
    }

    private static String stripXss(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value;
        for (Pattern pattern : XSS_PATTERNS) {
            cleaned = pattern.matcher(cleaned).replaceAll("");
        }
        return cleaned;
    }

    private static class XssRequestWrapper extends HttpServletRequestWrapper {

        public XssRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            return stripXss(super.getParameter(name));
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }
            String[] cleaned = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleaned[i] = stripXss(values[i]);
            }
            return cleaned;
        }

        @Override
        public String getHeader(String name) {
            return stripXss(super.getHeader(name));
        }
    }
}
