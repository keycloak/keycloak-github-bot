package org.keycloak.gh.bot.security.command;

import org.kohsuke.github.GHEventPayload;

import java.io.IOException;

public interface BotCommand {
    void run(GHEventPayload.IssueComment issueCommentPayload) throws IOException;
}