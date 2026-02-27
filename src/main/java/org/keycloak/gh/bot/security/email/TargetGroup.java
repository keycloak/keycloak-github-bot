package org.keycloak.gh.bot.security.email;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Encapsulates the Google Group identity, structural validation, and URL generation. */
public record TargetGroup(String email, String id, String domain) {

    public static TargetGroup from(String emailAddress) {
        if (emailAddress == null || !emailAddress.contains("@")) {
            throw new IllegalArgumentException("Invalid target group email configuration: " + emailAddress);
        }
        var parts = emailAddress.split("@");
        return new TargetGroup(emailAddress, parts[0], parts[1]);
    }

    public String getArchiveLink(String messageId) {
        var domainPath = "googlegroups.com".equals(domain) ? "" : "a/%s/".formatted(domain);
        var query = URLEncoder.encode("rfc822msgid:" + messageId, StandardCharsets.UTF_8);

        return "https://groups.google.com/%sg/%s/search?q=%s".formatted(domainPath, id, query);
    }

    public boolean matchesListId(String listIdHeader) {
        return listIdHeader != null && listIdHeader.contains(id);
    }
}