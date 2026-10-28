package com.andres.course.claude.quarkus.quarkusarc.app.config;

import com.andres.course.claude.quarkus.quarkusarc.app.models.Item;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    // Registra otra List<Item>, cargada desde src/main/resources/items.json; se inyecta con @ItemsJson
    @Produces
    @ItemsJson
    public List<Item> itemsFromJson(ObjectMapper mapper) throws IOException {
        try (InputStream json = Thread.currentThread().getContextClassLoader().getResourceAsStream("items.json")) {
            return mapper.readValue(json, new TypeReference<List<Item>>() {
            });
        }
    }

    // Registra otra List<Item>, cargada desde el archivo plano src/main/resources/items.csv
    // (encabezado + una línea por item: product,price,quantity); se inyecta con @ItemsFile
    @Produces
    @ItemsFile
    public List<Item> itemsFromFile() throws IOException {
        try (InputStream file = Thread.currentThread().getContextClassLoader().getResourceAsStream("items.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1) // encabezado
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(line -> line.split(","))
                    .map(fields -> new Item(fields[0].trim(), new BigDecimal(fields[1].trim()), Integer.valueOf(fields[2].trim())))
                    .toList();
        }
    }
}
