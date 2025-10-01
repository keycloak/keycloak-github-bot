package org.keycloak.gh.bot.labels;

public enum Kind {

    BUG;

    @Override
    public String toString() {
        return EnumUtils.toLabel(this);
    }

    public String toLabel() {
        return EnumUtils.toLabel(this);
    }

    public static boolean isInstance(String label) {
        return EnumUtils.isInstance(label, Kind.class);
    }

    public static Kind fromKind(String kind) {
        return EnumUtils.fromValue(kind, Kind.class);
    }

    public static Kind fromLabel(String label) {
        return EnumUtils.fromLabel(label, Kind.class);
    }

}
