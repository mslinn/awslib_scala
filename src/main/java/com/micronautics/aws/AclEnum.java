package com.micronautics.aws;

public enum AclEnum {
    publicAcl("public-read"),
    privateAcl("private");

    private final String display;

    AclEnum(String display) {
        this.display = display;
    }

    String display() { return display; }
}
