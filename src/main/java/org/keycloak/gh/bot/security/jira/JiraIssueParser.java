package org.keycloak.gh.bot.security.jira;

import jakarta.enterprise.context.ApplicationScoped;
import org.keycloak.gh.bot.security.common.Constants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class JiraIssueParser {

    private static final Pattern BUILD_SUFFIX = Pattern.compile("\\[.*?\\]$");

    private static final Pattern GENERIC_PREFIX = Pattern.compile("^([\\w\\-\\.\\/]+):\\s+");

    private static final Pattern FLAW_START = Pattern.compile("(?i)^Flaw(?: Description)?:?\\s*$");
    private static final Pattern FLAW_END = Pattern.compile("(?i)^(?:Additional notes|Mitigation|Statement|References).*");

    public String parseTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) return "";

        Matcher cveMatcher = Constants.CVE_PATTERN.matcher(rawTitle);
        String cveId = cveMatcher.find() ? cveMatcher.group() : "";

        String cleanText = rawTitle.replace(cveId, "")
                .replaceAll("(?i)EMBARGOED", "")
                .trim();

        cleanText = cleanText.replaceAll("^[\\W_]+", "");

        String backupText = cleanText;

        Matcher prefixMatcher = GENERIC_PREFIX.matcher(cleanText);
        while (prefixMatcher.find()) {
            cleanText = prefixMatcher.replaceFirst("").trim();
            prefixMatcher = GENERIC_PREFIX.matcher(cleanText);
        }

        if (cleanText.isEmpty()) {
            cleanText = backupText;
        }

        cleanText = BUILD_SUFFIX.matcher(cleanText).replaceAll("").trim();

        if (cleanText.isEmpty()) {
            cleanText = backupText;
        }

        return cveId + " " + cleanText;
    }

    public String parseDescription(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) return "";

        String[] lines = rawDescription.split("\\R");
        StringBuilder flawContent = new StringBuilder();
        boolean insideFlaw = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (!insideFlaw && FLAW_START.matcher(trimmed).matches()) {
                insideFlaw = true;
                continue;
            }

            if (insideFlaw && FLAW_END.matcher(trimmed).matches()) {
                break;
            }

            if (insideFlaw) {
                if (flawContent.isEmpty() && GENERIC_PREFIX.matcher(trimmed).lookingAt()) {
                    continue;
                }
                flawContent.append(line).append("\n");
            }
        }

        return flawContent.toString().trim();
    }
}