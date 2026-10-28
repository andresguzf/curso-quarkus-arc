package com.andres.course.claude.quarkus.quarkusarc.app.models;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Customer {

    private String name = "Andrés";
    private String lastname = "Guzmán";

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
