package org.keycloak.gh.bot.labels;

import java.util.Locale;

public enum Label {

    HELP_WANTED;

    @Override
    public String toString() {
        return toLabel();
    }

    public String toLabel() {
        return name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
    }

}
