package com.andres.course.claude.quarkus.quarkusarc.app.models;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.andres.course.claude.quarkus.quarkusarc.app.config.ItemsFile;
// import com.andres.course.claude.quarkus.quarkusarc.app.config.ItemsJson;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Una sola factura compartida por toda la aplicación (con @SessionScoped habría una por sesión HTTP)
@ApplicationScoped
public class Invoice {

    private Customer customer;

    @ConfigProperty(name = "invoice.description", defaultValue = "Invoice copras de supermercado ...")
    String description;

    private List<Item> items;

    // Momento en que ArC terminó de crear la instancia real (lo asigna init())
    private Instant createdAt;

    public Invoice(Customer customer, @ItemsFile List<Item> items) {
        this.customer = customer;
        this.items = items;
    }

    /**
     * Callback {@code @PostConstruct}: ArC lo llama una sola vez por instancia, justo después del
     * constructor y de la inyección de campos ({@code @ConfigProperty}). Por eso aquí, a diferencia
     * del constructor, {@code description} ya tiene su valor.
     * <p>
     * Con {@code @ApplicationScoped} el bean es lazy: lo que se inyecta en {@code InvoiceController} es un
     * client proxy, y la instancia real (y este callback) se crea con la primera llamada a un método de la
     * factura, no al arrancar la aplicación. Luego se reutiliza en todas las peticiones, así que corre una
     * vez por ciclo de vida de la aplicación (en dev mode, de nuevo tras cada live reload).
     * <p>
     * La concatenación sobre el nombre del cliente deja ver cuántas veces corre este callback:
     * {@code Customer} es {@code @ApplicationScoped} (una sola instancia compartida), así que cada nueva
     * instancia de la factura le suma otro sufijo. Con {@code @ApplicationScoped} se ve una sola vez;
     * con {@code @SessionScoped} el nombre crecería con cada sesión nueva.
     */
    @PostConstruct
    void init() {
        customer.setName(customer.getName().concat(" (PostConstruct)"));
        createdAt = Instant.now();
        Log.infof("Factura creada (@PostConstruct): cliente=%s, items=%d, monto=%s",
                customer, items.size(), getAmount());
    }

    /**
     * Callback {@code @PreDestroy}: ArC lo llama una sola vez, antes de descartar la instancia.
     * <p>
     * Con {@code @ApplicationScoped} eso ocurre cuando se destruye el contexto de aplicación: al detener
     * la aplicación (Ctrl+C, fin de las pruebas) o al reiniciarse en un live reload de dev mode. Invalidar
     * la sesión ({@code POST /api/logout}) no la afecta.
     */
    @PreDestroy
    void destroy() {
        Log.infof("Factura destruida (@PreDestroy): creada en %s, se liberan %d items", createdAt, items.size());
        items = List.of();
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return super.toString().concat(": Invoice{customer=" + customer + ", description='" + description + "', amount=" + getAmount() + ", items=" + items + "}");
    }
}
