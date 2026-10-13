# Keycloak GitHub Bot

GitHub Application to help manage issues and PRs for the Keycloak project. 

The bot is currently deployed to https://openshift-console.osci.io/.

## What the application does

### Add area labels to bug reports

The bug issue form for the Keycloak project has a dropdown for selecting the affected area. The bot will automatically 
add the applicable `area/` when a new bug issue is created.

### Remove `status/triage` label when an issue is closed

When an issue is closed the `status/triage` label is removed from the issue.

### Remind assignees about missing backport PRs

Every hour, the bot checks issues closed as completed for at least 24 hours, with a related PR merged into `main`.
It mentions assignees about missing backports and repeats reminders daily until satisfied, preserving history across restarts.

Each `backport/<version>` label requires an open or merged PR targeting `release/<version>`, linked in Development.
The bot links matching release PRs when opened, edited, or reopened, using closing keywords such as `Closes #123`.
The hourly check only verifies Development links and sends reminders; linking is handled by PR webhooks.
It ignores `backport/main` and versions with an `archive/release/<version>` branch. Removing a backport label removes its requirement.

Configure the schedule with `backport.cron` and the reminder interval with `backport.reminderIntervalDays` (default: 1).
Automatic linking requires GitHub App permission to link PRs to issues.

## Testing changes to the application

To test changes to the application you need:

* Create a testing GitHub Application
* Install the above into a testing repository on GitHub
* A smee.io channel to receive webhooks from GitHub locally
* A `.env` file in the repository

Example `.env` file:

```
QUARKUS_GITHUB_APP_APP_ID=123456
QUARKUS_GITHUB_APP_APP_NAME=Keycloak Bot Testing
QUARKUS_GITHUB_APP_WEBHOOK_PROXY_URL=https://smee.io/1234256
QUARKUS_GITHUB_APP_WEBHOOK_SECRET=12346
QUARKUS_GITHUB_APP_PRIVATE_KEY=-----BEGIN RSA PRIVATE KEY-----\
1234256789\
-----END RSA PRIVATE KEY-----
```

When the above has been done you can run the application locally using:

```shell script
./mvnw compile quarkus:dev
```

## Related Guides

- GitHub App ([guide](https://quarkiverse.github.io/quarkiverse-docs/quarkus-github-app/dev/index.html)): Automate GitHub tasks with a GitHub App
