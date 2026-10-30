package com.andres.course.claude.quarkus.quarkusarc.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * Ajusta el ObjectMapper que usan Quarkus REST y RequestTimeFilter.
 * <p>
 * RequestTimeFilter convierte la respuesta a un árbol JSON con {@code valueToTree}, y por defecto Jackson le
 * quita los ceros finales a los {@code BigDecimal} al hacerlo (1.0000 pasaría a 1). Así se conservan los
 * 4 decimales del resultado de {@code GreetingController.hello()}.
 */
@Singleton
public class JacksonConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
    }
}
