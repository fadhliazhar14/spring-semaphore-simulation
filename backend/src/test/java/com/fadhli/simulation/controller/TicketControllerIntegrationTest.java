package com.fadhli.simulation.controller;

import com.fadhli.simulation.dto.InitSimulationRequest;
import com.fadhli.simulation.dto.SimulationStatusDto;
import com.fadhli.simulation.model.TicketEvent;
import com.fadhli.simulation.service.SseService;
import com.fadhli.simulation.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketController.class)
class TicketControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private SseService sseService;

    @Test
    void testInitSimulationEndpoint() throws Exception {
        InitSimulationRequest req = new InitSimulationRequest("Coldplay", 50, 5);
        TicketEvent mockEvent = new TicketEvent("Coldplay", 50, 50);
        mockEvent.setId(1L);

        when(ticketService.initSimulation(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(mockEvent);

        mockMvc.perform(post("/api/simulation/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Coldplay"))
                .andExpect(jsonPath("$.totalTickets").value(50));
    }

    @Test
    void testGetStatusEndpoint() throws Exception {
        SimulationStatusDto mockStatus = new SimulationStatusDto(
                50, 100, 2, 5, 10, 100, 50, 0, 0, "Running"
        );

        when(ticketService.getCurrentStatus()).thenReturn(mockStatus);

        mockMvc.perform(get("/api/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTickets").value(50))
                .andExpect(jsonPath("$.totalPermits").value(5))
                .andExpect(jsonPath("$.activePermits").value(2));
    }

    @Test
    void testStreamEndpoint() throws Exception {
        when(sseService.subscribe()).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/simulation/stream"))
                .andExpect(status().isOk());
    }
}
