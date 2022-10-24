# Keycloak GitHub Bot

GitHub Application to help manage issues and PRs for the Keycloak project.

## What the application does

### Add area labels to bug reports

The bug issue form for the Keycloak project has a dropdown for selecting the affected area. The bot will automatically 
add the applicable `area/` when a new bug issue is created.

### Remove `status/triage` label when an issue is closed

When an issue is closed the `status/triage` label is removed from the issue.

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
