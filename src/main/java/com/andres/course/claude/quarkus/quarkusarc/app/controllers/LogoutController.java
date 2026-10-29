package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import io.quarkus.logging.Log;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/api/logout")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Session")
public class LogoutController {

    /**
     * Invalida la sesión HTTP: Undertow destruye el contexto {@code @SessionScoped} y ArC llama
     * {@code @PreDestroy} en la {@code Invoice} de esa sesión. La siguiente petición a
     * {@code /api/invoices} abre una sesión nueva y crea otra factura.
     */
    @POST
    @Operation(summary = "Invalida la sesión actual y destruye la factura @SessionScoped")
    public Map<String, String> logout(@Context HttpServletRequest request) {
        // getSession(false): solo la recupera si existe, no crea una nueva para invalidarla
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Map.of("message", "No había una sesión activa");
        }
        String id = session.getId();
        session.invalidate();
        Log.infof("Sesión %s invalidada", id);
        return Map.of("message", "Sesión invalidada");
    }
}
