package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class InvoiceControllerTest {

    // Cookie de sesión que Undertow asigna al abrir la HttpSession
    private static final String COOKIE = "JSESSIONID";

    @Test
    void showDevuelveLaFacturaInicializadaPorPostConstruct() {
        given()
                .when().get("/api/invoices")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("customer.name", not(emptyOrNullString()))
                .body("description", not(emptyOrNullString()))
                .body("items.size()", greaterThan(0))
                .body("amount", greaterThan(0f))
                // createdAt lo asigna @PostConstruct; si el callback no corriera, llegaría null
                .body("createdAt", notNullValue())
                // Lo agrega RequestTimeFilter en el filtro de respuesta
                .body("elapsedTime", endsWith(" ms"));
    }

    @Test
    void rutaInexistenteDevuelve404() {
        // Sin quarkus-rest-servlet, Quarkus REST responde su 404 nativo a una ruta que no coincide con ningún @Path
        given()
                .when().get("/api/no-existe")
                .then()
                .statusCode(404);
    }

    @Test
    void showReutilizaLaMismaInstanciaApplicationScoped() {
        String first = given().when().get("/api/invoices").then().statusCode(200).extract().path("createdAt");
        String second = given().when().get("/api/invoices").then().statusCode(200).extract().path("createdAt");

        // @PostConstruct corre una sola vez por instancia: con @ApplicationScoped ambas peticiones ven la misma
        assertEquals(first, second);
    }

    @Test
    void showNoAbreSesionHttp() {
        Response response = given().when().get("/api/invoices");

        // Con @ApplicationScoped nada usa la sesión, así que no se crea ni se envía la cookie
        response.then().statusCode(200);
        assertNull(response.getCookie(COOKIE));
    }

    @Test
    @Disabled("Requiere quarkus-rest-servlet (comentada en el pom): sin ella no hay HttpServletRequest ni sesión")
    void logoutSinSesionNoFallaNiDestruyeLaFactura() {
        String before = given().when().get("/api/invoices").then().statusCode(200).extract().path("createdAt");

        given()
                .when().post("/api/logout")
                .then()
                .statusCode(200)
                .body("message", equalTo("No había una sesión activa"));

        // La factura vive en el contexto de aplicación: cerrar la sesión no la toca
        String after = given().when().get("/api/invoices").then().statusCode(200).extract().path("createdAt");
        assertEquals(before, after);
    }
}
