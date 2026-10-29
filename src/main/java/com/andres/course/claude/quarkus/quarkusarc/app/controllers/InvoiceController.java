package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import com.andres.course.claude.quarkus.quarkusarc.app.models.Invoice;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Invoices")
public class InvoiceController {

    private final Invoice invoice;

    // Metadatos que CDI tiene de este mismo bean (scope, tipos, qualifiers); el contenedor lo inyecta solo
    private final Bean<InvoiceController> bean;

    public InvoiceController(Invoice invoice, Bean<InvoiceController> bean) {
        this.invoice = invoice;
        this.bean = bean;
    }

    // Sin scope explícito, Quarkus REST trata el controller como @Singleton: se crea una vez, con la primera petición
    @PostConstruct
    void init() {
        Log.infof("InvoiceController creado (@PostConstruct), scope: @%s", bean.getScope().getSimpleName());
    }

    // Se llama una sola vez, al detener la aplicación
    @PreDestroy
    void destroy() {
        Log.info("InvoiceController destruido (@PreDestroy)");
    }

    @GET
    @Operation(summary = "Devuelve la factura con su cliente inyectado")
    public Invoice show() {
        Log.infof("Factura: %s", invoice);
        return invoice;
    }
}
