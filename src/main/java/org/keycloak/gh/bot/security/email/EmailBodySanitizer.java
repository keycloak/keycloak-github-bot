package org.keycloak.gh.bot.security.email;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class EmailBodySanitizer {

    // Matches standard email signatures to strip them out
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("(?m)^--\\s*$|^You received this message because you are subscribed.*");

    // Finds the very beginning of the thread history
    private static final Pattern THREAD_HISTORY_PATTERN = Pattern.compile(
            "(?ism)(" +
                    "^_{32,}|" +                             // Outlook HTML-to-Plain divider
                    "^-----Original Message-----|" +         // Outlook Plain divider
                    "^From:\\s*.{5,200}?\\s*Sent:|" +        // Exchange alternative header
                    "^On\\s*.{5,200}?\\s*wrote:|" +          // Gmail / Apple Mail (English)
                    "^>+" +                                  // fallback: the first quote character on a new line
                    ")"
    );

    public Optional<String> sanitize(String body) {
        if (body == null || body.isBlank()) return Optional.empty();

        String content = stripSignature(body);
        if (content.isBlank()) return Optional.empty();

        Matcher matcher = THREAD_HISTORY_PATTERN.matcher(content);

        // If we find any thread history marker, split the email right there.
        if (matcher.find()) {
            int splitIndex = matcher.start();

            // Ensure there is actually fresh text before we collapse the rest
            if (splitIndex > 0) {
                String fresh = content.substring(0, splitIndex).strip();
                String quoted = content.substring(splitIndex).strip();

                if (!fresh.isEmpty()) {
                    return Optional.of(fresh + "\n\n<details>\n<summary>Thread history</summary>\n\n" + quoted + "\n</details>");
                }
            }
        }

        return Optional.of(content);
    }

    private String stripSignature(String body) {
        Matcher matcher = SIGNATURE_PATTERN.matcher(body);
        return matcher.find() ? body.substring(0, matcher.start()).strip() : body.strip();
    }
}