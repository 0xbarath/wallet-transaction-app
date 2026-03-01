package com.wallet.txhistory.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(4)
public class AdminEnrichmentFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String role = (String) request.getAttribute(RbacFilter.ROLE_ATTRIBUTE);
        if (!RbacFilter.isAdmin(role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"https://example.com/problems/forbidden","title":"Forbidden","status":403,"detail":"Admin role required"}""");
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (FilterPaths.isExcluded(request)) {
            return true;
        }
        String uri = request.getRequestURI();
        return !uri.endsWith("/explain");
    }
}
