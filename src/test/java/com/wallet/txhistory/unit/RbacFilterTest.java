package com.wallet.txhistory.unit;

import com.wallet.txhistory.filter.RbacFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RbacFilterTest {

    private RbacFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RbacFilter();
    }

    @Test
    void defaultsToUserRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/wallets");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(RbacFilter.ROLE_ATTRIBUTE)).isEqualTo("user");
    }

    @Test
    void extractsAdminRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/wallets");
        request.addHeader("X-Role", "admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(RbacFilter.ROLE_ATTRIBUTE)).isEqualTo("admin");
    }

    @Test
    void rejectsInvalidRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/wallets");
        request.addHeader("X-Role", "superuser");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void normalizesToLowercase() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/wallets");
        request.addHeader("X-Role", "Admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(RbacFilter.ROLE_ATTRIBUTE)).isEqualTo("admin");
    }

    @Test
    void skipsActuatorEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Filter should skip, so no role attribute is set
        assertThat(request.getAttribute(RbacFilter.ROLE_ATTRIBUTE)).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotSkipApiEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/transactions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Filter should process and set the default role
        assertThat(request.getAttribute(RbacFilter.ROLE_ATTRIBUTE)).isEqualTo("user");
    }
}
