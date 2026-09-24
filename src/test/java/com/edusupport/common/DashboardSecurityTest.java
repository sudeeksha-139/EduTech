package com.edusupport.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import com.edusupport.security.JwtAuthenticationFilter;
import com.edusupport.security.JwtService;
import com.edusupport.security.SecurityConfig;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class DashboardSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean DashboardService dashboardService;

    @Test
    void rejectsMissingJwt() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsStaffFromAdminDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary").with(user("staff@example.edu").roles("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminDashboardAccess() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary").with(user("admin@example.edu").roles("ADMIN")))
                .andExpect(status().isOk());
    }

        @Test
        void studentCannotAccessStaffOrAdminDashboards() throws Exception {
        mockMvc.perform(get("/api/dashboard/staff").with(user("student@example.edu").roles("STUDENT")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/admin").with(user("student@example.edu").roles("STUDENT")))
            .andExpect(status().isForbidden());
        }

        @Test
        void staffCannotAccessStudentOrAdminDashboards() throws Exception {
        mockMvc.perform(get("/api/dashboard/student").with(user("staff@example.edu").roles("STAFF")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/admin").with(user("staff@example.edu").roles("STAFF")))
            .andExpect(status().isForbidden());
        }

        @Test
        void adminCanAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/admin").with(user("admin@example.edu").roles("ADMIN")))
            .andExpect(status().isOk());
        }
}
