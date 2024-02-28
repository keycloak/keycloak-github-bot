package org.keycloak.gh.bot.labels;

public enum Status {

    AUTO_EXPIRE,
    AUTO_BUMP,
    EXPIRED_BY_BOT,
    MISSING_INFORMATION,
    BUMPED_BY_BOT,
    TRIAGE,
    REOPENED;

    @Override
    public String toString() {
        return EnumUtils.toLabel(this);
    }

    public String toLabel() {
        return EnumUtils.toLabel(this);
    }

    public static boolean isInstance(String label) {
        return EnumUtils.isInstance(label, Status.class);
    }

    public static Status fromStatus(String status) {
        return EnumUtils.fromValue(status, Status.class);
    }

    public static Status fromLabel(String label) {
        return EnumUtils.fromLabel(label, Status.class);
    }

}
