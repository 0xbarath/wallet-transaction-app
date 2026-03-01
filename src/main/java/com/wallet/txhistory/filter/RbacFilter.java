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
import java.util.Set;

@Component
@Order(3)
public class RbacFilter extends OncePerRequestFilter {

    public static final String ROLE_ATTRIBUTE = "x-role";
    private static final String ROLE_HEADER = "X-Role";
    private static final String ADMIN_ROLE = "admin";
    private static final Set<String> VALID_ROLES = Set.of(ADMIN_ROLE, "user");
    private static final String DEFAULT_ROLE = "user";

    public static boolean isAdmin(String role) {
        return ADMIN_ROLE.equalsIgnoreCase(role);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String role = request.getHeader(ROLE_HEADER);
        if (role == null || role.isBlank()) {
            role = DEFAULT_ROLE;
        }
        role = role.toLowerCase();

        if (!VALID_ROLES.contains(role)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Bad Request","status":400,"detail":"Invalid X-Role header. Must be 'admin' or 'user'"}""");
            return;
        }

        request.setAttribute(ROLE_ATTRIBUTE, role);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return FilterPaths.isExcluded(request);
    }
}
