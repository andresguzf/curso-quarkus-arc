package com.andres.course.claude.quarkus.quarkusarc.app.models;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.andres.course.claude.quarkus.quarkusarc.app.config.ItemsFile;
// import com.andres.course.claude.quarkus.quarkusarc.app.config.ItemsJson;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class Invoice {

    private Customer customer;

    @ConfigProperty(name = "invoice.description", defaultValue = "Invoice copras de supermercado ...")
    String description;

    private List<Item> items;

    public Invoice(Customer customer, @ItemsFile List<Item> items) {
        this.customer = customer;
        this.items = items;
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

    // Total calculado: suma de precio * cantidad de cada item
    public BigDecimal getAmount() {
        return items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return super.toString().concat(": Invoice{customer=" + customer + ", description='" + description + "', amount=" + getAmount() + ", items=" + items + "}");
    }
}
