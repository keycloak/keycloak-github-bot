package org.keycloak.gh.bot.utils;

import java.util.LinkedList;
import java.util.List;

public class FlakyTest {

    private FlakyJob flakyJob;
    private String className;
    private String methodName;
    private List<String> failures = new LinkedList<>();

    public FlakyTest(FlakyJob flakyJob, String className, String methodName) {
        this.flakyJob = flakyJob;
        this.className = className;
        this.methodName = methodName;
    }

    public void addFailure(String stackTrace) {
        this.failures.add(stackTrace);
    }

    public FlakyJob getFlakyJob() {
        return flakyJob;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<String> getFailures() {
        return failures;
    }

    public String getIssueTitle() {
        return "Flaky test: " + getClassName() + "#" + getMethodName();
    }

    public String getIssueBody() {
        StringBuilder body = new StringBuilder();

        body.append("## ");
        body.append(getClassName());
        body.append("#");
        body.append(getMethodName());
        body.append("\n");

        body.append("[");
        body.append(flakyJob.getWorkflow());
        body.append(" - ");
        body.append(flakyJob.getJobName());
        body.append("](");
        body.append(flakyJob.getJobUrl());
        body.append(")");

        if (flakyJob.getPr() != null) {
            String pullRequestUrl = flakyJob.getPrUrl();
            String pullRequestNumber = flakyJob.getPr();

            body.append(" / [Pull Request #");
            body.append(pullRequestNumber);
            body.append("](");
            body.append(pullRequestUrl);
            body.append(")");
        }
        body.append("\n");

        for (String failure : getFailures()) {
            body.append("\n```\n");
            body.append(failure);
            body.append("\n```\n");
        }

        return body.toString();
    }

}
