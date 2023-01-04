package org.keycloak.gh.bot.utils;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FlakyJobParser {

    private static int FAILURE_MAX_LINES;

    public static FlakyJob parse(InputStream is) {
        FlakyJob flakyJob = new FlakyJob();
        FlakyTestHandler flakyTestHandler = new FlakyTestHandler(flakyJob);

        try {
            ZipInputStream zipInputStream = new ZipInputStream(is);
            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            saxParserFactory.setNamespaceAware(true);
            saxParserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            for (ZipEntry ze = zipInputStream.getNextEntry(); ze != null; ze = zipInputStream.getNextEntry()) {
                if (ze.getName().matches(".*/TEST-.*.xml")) {
                    SAXParser saxParser = saxParserFactory.newSAXParser();
                    saxParser.parse(CloseShieldInputStream.wrap(zipInputStream), flakyTestHandler);
                } else if (ze.getName().equals("job-summary.properties")) {
                    Properties properties = new Properties();
                    properties.load(CloseShieldInputStream.wrap(zipInputStream));

                    flakyJob.setJobName(properties.getProperty("job_name"));
                    flakyJob.setJobUrl(properties.getProperty("job_url"));
                    flakyJob.setPr(properties.getProperty("pr"));
                    flakyJob.setPrUrl(properties.getProperty("pr_url"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return flakyJob;
    }

    static class FlakyTestHandler extends DefaultHandler {

        FlakyJob flakyJob;
        FlakyTest currentFlakyTest = null;
        StringBuilder currentStackTrace = null;

        public FlakyTestHandler(FlakyJob flakyJob) {
            this.flakyJob = flakyJob;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (localName) {
                case "testcase":
                    String methodName = attributes.getValue("name");
                    String className = attributes.getValue("classname");

                    currentFlakyTest = new FlakyTest(flakyJob, className, methodName);
                    break;
                case "stackTrace":
                    currentStackTrace = new StringBuilder();
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (localName) {
                case "testcase":
                    if (!currentFlakyTest.getFailures().isEmpty()) {
                        flakyJob.addFlakyTest(currentFlakyTest);
                    }
                    currentFlakyTest = null;
                    break;
                case "stackTrace":
                    FAILURE_MAX_LINES = 5;
                    currentFlakyTest.addFailure(StringUtils.trimLines(currentStackTrace.toString(), FAILURE_MAX_LINES, true));
                    currentStackTrace = null;
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (currentStackTrace != null) {
                currentStackTrace.append(new String(ch, start, length).trim());
            }
        }

    }

}
