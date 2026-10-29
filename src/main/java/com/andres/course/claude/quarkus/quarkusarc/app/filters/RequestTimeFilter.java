package com.andres.course.claude.quarkus.quarkusarc.app.filters;

import io.quarkus.logging.Log;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Filtro HTTP global de Quarkus REST: mide cuánto tarda cada petición a cualquier endpoint y lo
 * escribe en el log. No hace falta registrarlo; Quarkus REST detecta los métodos anotados con
 * {@code @ServerRequestFilter} y {@code @ServerResponseFilter} y los aplica a todos los endpoints.
 * <ul>
 *   <li>Pre ({@link #before}): se ejecuta antes del endpoint y guarda la hora de inicio.</li>
 *   <li>Post ({@link #after}): se ejecuta después del endpoint y calcula el tiempo transcurrido.</li>
 * </ul>
 */
public class RequestTimeFilter {

    private static final String START_TIME = "request-start-time";

    // Pre: preMatching = true corre antes de buscar el endpoint, así también se mide un 404
    @ServerRequestFilter(preMatching = true)
    public void before(ContainerRequestContext request) {
        request.setProperty(START_TIME, System.nanoTime());
        Log.infof("Antes: %s %s", request.getMethod(), request.getUriInfo().getPath());
    }

    // Post: corre cuando el endpoint ya generó la respuesta, antes de enviarla al cliente
    @ServerResponseFilter
    public void after(ContainerRequestContext request, ContainerResponseContext response) {
        // Las propiedades viven lo que dura la petición; sirven para pasar el inicio al filtro de respuesta
        if (!(request.getProperty(START_TIME) instanceof Long start)) {
            return;
        }
        double millis = (System.nanoTime() - start) / 1_000_000.0;
        Log.infof("Después: %s %s -> %d en %.2f ms",
                request.getMethod(),
                request.getUriInfo().getPath(),
                response.getStatus(),
                millis);
    }
}
