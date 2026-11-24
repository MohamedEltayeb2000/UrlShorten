package com.example.shortener.governance;

/** Who is performing an action: a human user or an automated agent. */
public record Actor(String id, ActorType type) {
    public Actor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("actor id must not be blank");
        }
    }
}
