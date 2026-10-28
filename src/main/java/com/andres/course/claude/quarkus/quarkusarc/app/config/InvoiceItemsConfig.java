package com.andres.course.claude.quarkus.quarkusarc.app.config;

import io.smallrye.config.ConfigMapping;

import java.math.BigDecimal;
import java.util.List;

// Lee la lista invoice.detail.items[n].* desde application.properties.
// Prefijo propio para no chocar con invoice.description (@ConfigProperty)
@ConfigMapping(prefix = "invoice.detail")
public interface InvoiceItemsConfig {

    List<ItemConfig> items();

    interface ItemConfig {
        String product();

        BigDecimal price();

        Integer quantity();
    }
}
