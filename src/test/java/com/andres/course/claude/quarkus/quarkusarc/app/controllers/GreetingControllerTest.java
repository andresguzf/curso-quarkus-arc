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
    void helloSaludaPorDefectoAlMundo() {
        given()
                .when().get("/api/greetings/hello")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Hola, Mundo! Bienvenido al curso de Quarkus"))
                .body("name", equalTo("Mundo"))
                .body("elapsedTime", endsWith(" ms"));
    }

    @Test
    void helloUsaElNombreRecibido() {
        given()
                .queryParam("name", "Andrés")
                .when().get("/api/greetings/hello")
                .then()
                .statusCode(200)
                .body("message", containsString("Hola, Andrés!"))
                .body("name", equalTo("Andrés"));
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
