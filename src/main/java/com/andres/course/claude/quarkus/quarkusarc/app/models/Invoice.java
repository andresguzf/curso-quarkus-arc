package com.andres.course.claude.quarkus.quarkusarc.app.models;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Invoice {

    // Inyección por atributo; package-private para que ArC no use reflexión
    @Inject
    Customer customer;

    // Valor desde application.properties
    @ConfigProperty(name = "invoice.description", defaultValue = "Invoice copras de supermercado ...")
    String description;

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
