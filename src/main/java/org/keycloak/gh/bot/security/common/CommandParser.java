package org.keycloak.gh.bot.security.common;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.keycloak.gh.bot.GitHubInstallationProvider;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class CommandParser {

    @Inject
    GitHubInstallationProvider gitHubProvider;

    private String botName;
    private Pattern mentionPattern;

    private static final Pattern NEW_SECALERT_PATTERN = Pattern.compile("^/new\\s+secalert\\s+\"([^\"]+)\"[ \\t]*(?:[\\r\\n]+(.*))?$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLY_PATTERN = Pattern.compile("^/reply\\s+keycloak-security[ \\t]*(?:[\\r\\n]+(.*))?$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLY_SECALERT_PATTERN = Pattern.compile("^/reply\\s+secalert[ \\t]*(?:[\\r\\n]+(.*))?$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    @PostConstruct
    public void init() {
        String rawLogin = gitHubProvider.getBotLogin();
        this.botName = (rawLogin != null && rawLogin.endsWith("[bot]"))
                ? rawLogin.replace("[bot]", "")
                : rawLogin;

        String name = Pattern.quote(this.botName != null ? this.botName : "unknown");
        this.mentionPattern = Pattern.compile("(?mi)^@" + name + "(?:\\[bot\\])?\\b");
    }

    public String getBotName() {
        return botName;
    }

    public String getHelpMessage() {
        return Constants.HELP_MESSAGE;
    }

    public Optional<Command> parse(String body) {
        if (body == null || body.isBlank()) return Optional.empty();

        String trimmed = body.trim();
        Matcher mentionMatcher = mentionPattern.matcher(trimmed);

        if (!mentionMatcher.find()) return Optional.empty();

        String args = trimmed.substring(mentionMatcher.end()).trim();

        Matcher newMatcher = NEW_SECALERT_PATTERN.matcher(args);
        if (newMatcher.matches()) {
            return Optional.of(new Command(CommandType.NEW_SECALERT, Optional.of(newMatcher.group(1)), newMatcher.group(2)));
        }

        Matcher replyMatcher = REPLY_PATTERN.matcher(args);
        if (replyMatcher.matches()) {
            return Optional.of(new Command(CommandType.REPLY_KEYCLOAK_SECURITY, Optional.empty(), replyMatcher.group(1)));
        }

        Matcher replySecAlertMatcher = REPLY_SECALERT_PATTERN.matcher(args);
        if (replySecAlertMatcher.matches()) {
            return Optional.of(new Command(CommandType.REPLY_SECALERT, Optional.empty(), replySecAlertMatcher.group(1)));
        }

        return Optional.of(new Command(CommandType.UNKNOWN, Optional.empty(), args));
    }

    public enum CommandType {
        NEW_SECALERT, REPLY_KEYCLOAK_SECURITY, REPLY_SECALERT, UNKNOWN
    }

    public record Command(CommandType type, Optional<String> subject, String body) {}
}