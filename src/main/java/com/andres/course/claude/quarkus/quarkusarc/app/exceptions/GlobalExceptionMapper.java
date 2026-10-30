package com.andres.course.claude.quarkus.quarkusarc.app.exceptions;

import io.quarkus.logging.Log;
import jakarta.ws.rs.NotFoundException;
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

    /**
     * Quarkus REST lanza {@code NotFoundException} cuando ninguna ruta coincide con la petición. También la
     * puede lanzar un controller cuando no encuentra el recurso pedido.
     * <p>
     * Para las rutas inexistentes solo funciona sin servlet: si {@code quarkus-undertow} está en el pom, Quarkus
     * REST no usa este método y responde la página 404 de Vert.x en HTML.
     */
    @ServerExceptionMapper
    public Response handleNotFoundException(NotFoundException exception, UriInfo uriInfo) {
        Log.warnf("Recurso no encontrado: %s", uriInfo.getPath());

        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of(
                        "error", "Recurso no encontrado",
                        "message", "La ruta o el recurso que buscas no existe. Revisa la URL e inténtalo de nuevo.",
                        "path", uriInfo.getPath()))
                .build();
    }
}
