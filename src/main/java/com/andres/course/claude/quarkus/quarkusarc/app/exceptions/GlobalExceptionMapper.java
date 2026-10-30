package com.andres.course.claude.quarkus.quarkusarc.app.exceptions;

import io.quarkus.logging.Log;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Map;

/**
 * Manejo global de excepciones: cada método {@code @ServerExceptionMapper} atiende un tipo de excepción
 * lanzada por cualquier controller y la convierte en una respuesta JSON con un mensaje simple.
 * <p>
 * Las excepciones sin método propio siguen mostrando la página de error nativa de Quarkus.
 */
public class GlobalExceptionMapper {

    @ServerExceptionMapper
    public Response handleArithmeticException(ArithmeticException exception, UriInfo uriInfo) {
        Log.warnf("Error aritmético en %s: %s", uriInfo.getPath(), exception.getMessage());

        // El tipo explícito permite que RequestTimeFilter reconozca la respuesta como JSON y agregue elapsedTime
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of(
                        "error", "División por cero",
                        "message", "No se puede dividir un número por cero. Revisa la operación e inténtalo de nuevo.",
                        "path", uriInfo.getPath()))
                .build();
    }
}
