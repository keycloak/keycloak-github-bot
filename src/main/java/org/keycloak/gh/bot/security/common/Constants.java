package org.keycloak.gh.bot.security.common;

import java.util.regex.Pattern;

public final class Constants {

    public static final Pattern CVE_PATTERN = Pattern.compile("CVE-\\d{4}-\\d+");

    public static final String GHI_ISSUE_PREFIX = "#GHI-";
    public static final Pattern GHI_ISSUE_PATTERN = Pattern.compile("#GHI-(\\d{1,9})");

    public static final String GMAIL_THREAD_ID_PREFIX = "**Gmail-Thread-ID:**";
    public static final String SECALERT_THREAD_ID_PREFIX = "**SecAlert-Thread-ID:**";
    public static final String CVE_TBD_PREFIX = "[CVE-TBD]";
    public static final String ISSUE_DESCRIPTION_TEMPLATE = "_Thread originally started in the keycloak-security mailing list. Replace the content here with a proper CVE description._";
    public static final String ATTACHMENTS_FOOTER = "\n_Attachments are not uploaded for security reasons. [View them in Google Groups](%s)_";

    private Constants() {
    }
}