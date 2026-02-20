package org.keycloak.gh.bot.security;

import com.github.rvesse.airline.annotations.Cli;
import io.quarkiverse.githubapp.command.airline.CliOptions;
import io.quarkiverse.githubapp.command.airline.CommandOptions;
import org.keycloak.gh.bot.security.command.MailingListCommand;
import org.keycloak.gh.bot.security.command.SecAlertCommand;
import org.keycloak.gh.bot.security.command.UnknownCommand;

@Cli(
        name = "@security",
        defaultCommand = UnknownCommand.class,
        commands = {
                MailingListCommand.class,
                SecAlertCommand.class,
                UnknownCommand.class
        }
)
@CliOptions(
        defaultCommandOptions = @CommandOptions(reactionStrategy = CommandOptions.ReactionStrategy.NONE)
)
public class SecurityBotCli {
}