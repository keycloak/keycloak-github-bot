package org.keycloak.gh.bot.labels;

public enum Action {

    QUESTION,
    MISSING_INFO,
    OLD_RELEASE,
    INVALID,
    PRIORITY_REGRESSION,
    PRIORITY_IMPORTANT,
    PRIORITY_NORMAL,
    PRIORITY_LOW;

    @Override
    public String toString() {
        return EnumUtils.toLabel(this);
    }

    public String toLabel() {
        return EnumUtils.toLabel(this);
    }

    public static boolean isInstance(String label) {
        return EnumUtils.isInstance(label, Action.class);
    }

    public static Action fromAction(String action) {
        return EnumUtils.fromValue(action, Action.class);
    }

    public static Action fromLabel(String label) {
        return EnumUtils.fromLabel(label, Action.class);
    }

}
