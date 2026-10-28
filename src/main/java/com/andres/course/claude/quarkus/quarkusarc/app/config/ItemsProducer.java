package com.andres.course.claude.quarkus.quarkusarc.app.config;

import com.andres.course.claude.quarkus.quarkusarc.app.models.Item;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@ApplicationScoped
public class ItemsProducer {

    // Registra List<Item> como bean, armado desde invoice.detail.items[n].* de application.properties
    @Produces
    public List<Item> items(InvoiceItemsConfig config) {
        return config.items().stream()
                .map(item -> new Item(item.product(), item.price(), item.quantity()))
                .toList();
    }
}
