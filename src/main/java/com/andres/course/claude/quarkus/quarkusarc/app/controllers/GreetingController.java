package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import com.andres.course.claude.quarkus.quarkusarc.app.filters.SkipRequestTime;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

@Path("/api/greetings")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Greetings")
public class GreetingController {

    private static final Locale SPANISH = Locale.of("es");

    @GET
    @Path("/hello")
    @Operation(summary = "Devuelve un saludo")
    public Map<String, String> hello(@QueryParam("name") @DefaultValue("Mundo") String name) {
        return Map.of(
                "message", "Hola, %s! Bienvenido al curso de Quarkus".formatted(name),
                "name", name);
    }

    // Excluido de RequestTimeFilter: no se mide en el log ni lleva elapsedTime
    @GET
    @Path("/date")
    @SkipRequestTime
    @Operation(summary = "Devuelve la fecha y hora actual del servidor")
    public Map<String, Object> date() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        return Map.of(
                "date", today.toString(),
                "time", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                "dayOfWeek", today.getDayOfWeek().getDisplayName(TextStyle.FULL, SPANISH),
                "formatted", now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", SPANISH)));
    }
}
