package org.keycloak.gh.bot.utils;

import java.util.LinkedList;
import java.util.List;

public class FlakyJob {

    public String workflow;
    public String jobName;
    public String jobUrl;
    public String pr;
    public String prUrl;

    private List<FlakyTest> flakyTests = new LinkedList<>();

    public String getWorkflow() {
        return workflow;
    }

    public String getJobName() {
        return jobName;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public String getPr() {
        return pr;
    }

    public String getPrUrl() {
        return prUrl;
    }

    public List<FlakyTest> getFlakyTests() {
        return flakyTests;
    }

    public void setWorkflow(String workflow) {
        this.workflow = workflow;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public void setPr(String pr) {
        this.pr = pr;
    }

    public void setPrUrl(String prUrl) {
        this.prUrl = prUrl;
    }

    public void addFlakyTest(FlakyTest flakyTest) {
        this.flakyTests.add(flakyTest);
    }
}
