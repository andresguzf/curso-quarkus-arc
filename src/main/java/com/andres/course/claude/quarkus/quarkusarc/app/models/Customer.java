package com.andres.course.claude.quarkus.quarkusarc.app.models;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Customer {

    // Valores desde application.properties
    @ConfigProperty(name = "customer.name", defaultValue = "John")
    String name;

    @ConfigProperty(name = "customer.lastname", defaultValue = "Doe")
    String lastname;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }
}
