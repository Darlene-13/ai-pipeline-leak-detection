package io.github.darlene.leakdetectionapplication.auth;


import lombok.Getter;


@Getter
public enum UserRole{

    ROLE_OPERATOR("operator"),
    ROLE_VIEWER("viewer");

    String description;


    UserRole(String description){
        this.description = description;
    }
}