package org.keycloak.gh.bot.security.common;

import java.util.regex.Pattern;

public final class Constants {

    public static final String SOURCE_EMAIL = "source/email";
    public static final String KIND_CVE = "kind/cve";
    public static final String CVE_TBD_PREFIX = "CVE-TBD";

    public static final String STATUS_JIRA_SYNCED = "status/jira-synced";

    public static final String GMAIL_THREAD_ID_PREFIX = "**Gmail-Thread-ID:**";
    public static final String SECALERT_THREAD_ID_PREFIX = "**SecAlert-Thread-ID:**";

    public static final String ISSUE_DESCRIPTION_TEMPLATE = "_Thread originally started in the keycloak-security mailing list. Replace the content here by a proper description._";

    public static final Pattern CVE_PATTERN = Pattern.compile("CVE-\\d{4}-\\d+");

    public static final String HELP_MESSAGE = """
            **Security Bot Help**
            
            Available commands:
            - `/new secalert "Subject" [Body]`: Create a new security alert email (auto-prefixes CVE-TBD).
            - `/reply keycloak-security [Body]`: Reply to the current security thread.
            - `/reply secalert [Body]`: Reply to the Red Hat SecAlert thread.
            
            Note: The command must be on the first line, and the body must start on a new line.
            """;

    private Constants() {
    }
}