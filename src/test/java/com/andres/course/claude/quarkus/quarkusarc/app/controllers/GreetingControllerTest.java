package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class GreetingControllerTest {

    @Test
    void helloDivide10PorElDivisorRecibido() {
        given()
                .queryParam("name", "Andrés")
                .queryParam("divisor", 3)
                .when().get("/api/greetings/hello")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Hola, Andrés! Bienvenido al curso de Quarkus"))
                .body("name", equalTo("Andrés"))
                // 10 / 3 redondeado a 4 decimales
                .body("result", equalTo(3.3333f))
                .body("elapsedTime", endsWith(" ms"));
    }

    @Test
    void helloSinDivisorUsa10() {
        given()
                .when().get("/api/greetings/hello")
                .then()
                .statusCode(200)
                .body("name", equalTo("Mundo"))
                .body("result", equalTo(1.0f));
    }

    @Test
    void helloRespondeLaDivisionPorCeroConUnMensajeSimple() {
        // Con divisor=0, hello lanza ArithmeticException y la atiende GlobalExceptionMapper
        given()
                .queryParam("divisor", 0)
                .when().get("/api/greetings/hello")
                .then()
                .statusCode(500)
                .contentType(ContentType.JSON)
                .body("error", equalTo("División por cero"))
                .body("message", containsString("No se puede dividir un número por cero"))
                .body("path", equalTo("/api/greetings/hello"))
                .body("elapsedTime", endsWith(" ms"));
    }

    @Test
    void dateDevuelveLaFechaActualSinElapsedTime() {
        given()
                .when().get("/api/greetings/date")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("date", equalTo(LocalDate.now().toString()))
                .body("dayOfWeek", not(emptyOrNullString()))
                .body("formatted", not(emptyOrNullString()))
                // @SkipRequestTime: RequestTimeFilter no toca este endpoint
                .body("$", not(hasKey("elapsedTime")));
    }
}
