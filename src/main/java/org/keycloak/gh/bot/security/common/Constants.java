package org.keycloak.gh.bot.security.common;

public final class Constants {

    public static final String GMAIL_THREAD_ID_PREFIX = "**Gmail-Thread-ID:**";
    public static final String ISSUE_DESCRIPTION_TEMPLATE = "_Thread originally started in the keycloak-security mailing list. Replace the content here with a proper CVE description._";
    public static final String ATTACHMENTS_FOOTER = "\n_Attachments are not uploaded for security reasons. [View them in Google Groups](%s)_";

    private Constants() {
    }
}