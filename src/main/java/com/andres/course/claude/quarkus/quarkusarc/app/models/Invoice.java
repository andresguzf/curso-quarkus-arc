package com.andres.course.claude.quarkus.quarkusarc.app.models;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;

@ApplicationScoped
public class Invoice {

    private Customer customer;

    // Valor desde application.properties
    @ConfigProperty(name = "invoice.description", defaultValue = "Invoice copras de supermercado ...")
    String description;

    @ConfigProperty(name = "invoice.amount", defaultValue = "0")
    BigDecimal amount;

    // Inyección por constructor; con un único constructor no hace falta @Inject
    public Invoice(Customer customer) {
        this.customer = customer;
    }

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Invoice{customer=" + customer + ", description='" + description + "', amount=" + amount + "}";
    }
}
