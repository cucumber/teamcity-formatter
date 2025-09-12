package io.cucumber.teamcityformatter;

import io.cucumber.messages.types.Location;

import java.util.Objects;

final class TreeNode {
    private final String name;
    private final String uri;
    private final Location location;

    TreeNode(String name, String uri, Location location) {
        this.name = name;
        this.uri = uri;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    public Location getLocation() {
        return location;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        TreeNode that = (TreeNode) o;
        return Objects.equals(name, that.name) && Objects.equals(uri, that.uri)
                && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uri, location);
    }
}
