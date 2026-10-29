package com.andres.course.claude.quarkus.quarkusarc.app.filters;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excluye un endpoint (o todos los de un controller) de {@link RequestTimeFilter}: no se mide en el
 * log ni se agrega {@code elapsedTime} al JSON de respuesta.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipRequestTime {
}
