package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Map;

/**
 * Responde 404 a las rutas que no coinciden con ningún otro controller.
 * <p>
 * Con {@code quarkus-rest-servlet}, Quarkus REST no sabe qué hacer cuando ningún {@code @Path} coincide:
 * no usa su mapper de 404 (lo desactiva si hay servlet) ni le devuelve la petición a Undertow, y termina en
 * un {@code NullPointerException} que responde 500. Esta ruta comodín hace que siempre haya un endpoint que
 * coincida. Quarkus REST elige primero el {@code @Path} más específico, así que solo llegan aquí las rutas
 * que no existen.
 */
@Path("/{path: .*}")
@Produces(MediaType.APPLICATION_JSON)
public class NotFoundController {

    @GET
    @Operation(hidden = true)
    public Response get(@PathParam("path") String path) {
        return notFound(path);
    }

    @POST
    @Operation(hidden = true)
    public Response post(@PathParam("path") String path) {
        return notFound(path);
    }

    @PUT
    @Operation(hidden = true)
    public Response put(@PathParam("path") String path) {
        return notFound(path);
    }

    @PATCH
    @Operation(hidden = true)
    public Response patch(@PathParam("path") String path) {
        return notFound(path);
    }

    @DELETE
    @Operation(hidden = true)
    public Response delete(@PathParam("path") String path) {
        return notFound(path);
    }

    private Response notFound(String path) {
        // El tipo explícito permite que RequestTimeFilter reconozca la respuesta como JSON y agregue elapsedTime
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("message", "Ruta no encontrada", "path", "/" + path))
                .build();
    }
}
