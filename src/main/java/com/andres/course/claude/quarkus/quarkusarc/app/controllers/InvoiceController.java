package com.andres.course.claude.quarkus.quarkusarc.app.controllers;

import com.andres.course.claude.quarkus.quarkusarc.app.models.Invoice;
import io.quarkus.logging.Log;
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

    public InvoiceController(Invoice invoice) {
        this.invoice = invoice;
    }

    @GET
    @Operation(summary = "Devuelve la factura con su cliente inyectado")
    public Invoice show() {
        Log.infof("Factura: %s", invoice);
        return invoice;
    }
}
