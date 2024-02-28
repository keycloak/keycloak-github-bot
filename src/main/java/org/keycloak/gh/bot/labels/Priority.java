package org.keycloak.gh.bot.labels;

public enum Priority {

    LOW,
    NORMAL,
    IMPORTANT,
    BLOCKER;

    @Override
    public String toString() {
        return EnumUtils.toLabel(this);
    }

    public String toLabel() {
        return EnumUtils.toLabel(this);
    }

    public static boolean isInstance(String label) {
        return EnumUtils.isInstance(label, Priority.class);
    }

    public static Priority fromPriority(String priority) {
        return EnumUtils.fromValue(priority, Priority.class);
    }

    public static Priority fromLabel(String label) {
        return EnumUtils.fromLabel(label, Priority.class);
    }

}
