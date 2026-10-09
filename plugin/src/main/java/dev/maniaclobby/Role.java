package dev.maniaclobby;

public enum Role {
    SURVIVOR("Выживший"),
    MANIAC("Маньяк");

    public final String title;

    Role(String title) {
        this.title = title;
    }

    public Role other() {
        return this == SURVIVOR ? MANIAC : SURVIVOR;
    }
}
