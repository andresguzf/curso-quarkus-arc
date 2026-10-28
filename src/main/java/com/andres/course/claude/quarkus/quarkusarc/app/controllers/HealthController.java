package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.Map;

@Path("/api/health")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health")
public class HealthController {

    @GET
    @Operation(summary = "Indica si la aplicación está arriba")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }
}
