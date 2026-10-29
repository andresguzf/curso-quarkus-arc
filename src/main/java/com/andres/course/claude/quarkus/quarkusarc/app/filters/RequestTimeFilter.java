package com.andres.course.claude.quarkus.quarkusarc.app.filters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Filtro HTTP global de Quarkus REST: mide cuánto tarda cada petición a cualquier endpoint, lo
 * escribe en el log y lo agrega al JSON de respuesta como {@code elapsedTime}. No hace falta
 * registrarlo; Quarkus REST detecta los métodos anotados con {@code @ServerRequestFilter} y
 * {@code @ServerResponseFilter} y los aplica a todos los endpoints.
 * <ul>
 *   <li>Pre ({@link #before}): se ejecuta antes del endpoint y guarda la hora de inicio.</li>
 *   <li>Post ({@link #after}): se ejecuta después del endpoint y calcula el tiempo transcurrido.</li>
 * </ul>
 */
public class RequestTimeFilter {

    private static final String START_TIME = "request-start-time";

    // El mismo ObjectMapper que usa Quarkus REST, con su configuración (fechas, módulos, etc.)
    private final ObjectMapper mapper;

    public RequestTimeFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

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
        addElapsedTime(response, millis);
    }

    /**
     * Aquí la entidad todavía es el objeto Java que devolvió el endpoint ({@code Invoice}, un
     * {@code Map}...); Jackson la serializa después de los filtros. Se convierte a un árbol JSON, se
     * le agrega el atributo y se reemplaza la entidad. Solo aplica a respuestas JSON que son un objeto:
     * un arreglo o un texto no tienen dónde guardar un atributo nuevo.
     */
    private void addElapsedTime(ContainerResponseContext response, double millis) {
        if (response.getEntity() == null || !MediaType.APPLICATION_JSON_TYPE.isCompatible(response.getMediaType())) {
            return;
        }
        JsonNode json = mapper.valueToTree(response.getEntity());
        if (json instanceof ObjectNode object) {
            object.put("elapsedTime", "%.2f ms".formatted(millis));
            response.setEntity(object);
        }
    }
}
