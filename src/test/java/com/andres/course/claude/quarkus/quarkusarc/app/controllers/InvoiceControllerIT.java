package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import io.quarkus.test.junit.QuarkusIntegrationTest;

// Repite las pruebas REST contra el jar empaquetado (./mvnw verify -DskipITs=false)
@QuarkusIntegrationTest
class InvoiceControllerIT extends InvoiceControllerTest {
}
