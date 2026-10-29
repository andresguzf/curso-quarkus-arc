package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
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
                .body("createdAt", notNullValue());
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

        // Con @ApplicationScoped nada usa la sesión, así que Undertow no la crea ni envía la cookie
        response.then().statusCode(200);
        assertNull(response.getCookie(COOKIE));
    }

    @Test
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
