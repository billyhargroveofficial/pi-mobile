package ru.billyhargrove.pimobile.core;

import java.util.Objects;

/** An Orca workspace identity as reported by GET /api/catalog. */
public final class Workspace {

    private final String id;
    private final String name;
    private final String path;

    public Workspace(String id, String name, String path) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.path = path == null ? "" : path;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String path() {
        return path;
    }

    /** Name to print: falls back to path and then to the id. */
    public String displayName() {
        if (!name.isEmpty()) {
            return name;
        }
        if (!path.isEmpty()) {
            return path;
        }
        return id.isEmpty() ? "Без имени" : id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Workspace)) {
            return false;
        }
        Workspace other = (Workspace) o;
        return id.equals(other.id) && name.equals(other.name) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, path);
    }

    @Override
    public String toString() {
        return "Workspace{" + id + ", " + name + "}";
    }
}
