package com.example.shortener.support;

import java.lang.annotation.*;

/** Links a test (or test class) to a requirement ID such as "001-FR-024". One ID per annotation. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Requirements.class)
public @interface Requirement {
    String value();
}
