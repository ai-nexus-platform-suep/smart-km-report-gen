package com.powerreport.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
 
class AdminHealthControllerTest {

    @Test
    void healthReturnsAdminServiceStatus() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminHealthController())
                .build();

        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.service").value("report-admin"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
