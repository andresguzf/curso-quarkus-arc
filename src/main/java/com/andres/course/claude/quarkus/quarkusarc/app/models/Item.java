package com.andres.course.claude.quarkus.quarkusarc.app.models;

import java.math.BigDecimal;

// Clase simple (no es bean CDI): una factura tiene varias instancias de Item
public class Item {

    private String product;
    private BigDecimal price;
    private Integer quantity;

    public Item(String product, BigDecimal price, Integer quantity) {
        this.product = product;
        this.price = price;
        this.quantity = quantity;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "Item{product='" + product + "', price=" + price + ", quantity=" + quantity + "}";
    }
}
