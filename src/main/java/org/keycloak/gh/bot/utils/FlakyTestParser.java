package org.keycloak.gh.bot.utils;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipInputStream;

public class FlakyTestParser {

    public static List<FlakyTest> parse(InputStream is) {
        FlakyTestHandler flakyTestHandler = new FlakyTestHandler();

        try {
            ZipInputStream zipInputStream = new ZipInputStream(is);
            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            saxParserFactory.setNamespaceAware(true);
            saxParserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            while (zipInputStream.getNextEntry() != null) {
                SAXParser saxParser = saxParserFactory.newSAXParser();
                saxParser.parse(CloseShieldInputStream.wrap(zipInputStream), flakyTestHandler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return flakyTestHandler.flakyTests;
    }

    static class FlakyTestHandler extends DefaultHandler {

        List<FlakyTest> flakyTests = new LinkedList<>();
        FlakyTest currentFlakyTest = null;
        StringBuilder currentStackTrace = null;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (localName) {
                case "testcase":
                    String methodName = attributes.getValue("name");
                    String className = attributes.getValue("classname");

                    currentFlakyTest = new FlakyTest(className, methodName);
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
                    flakyTests.add(currentFlakyTest);
                    currentFlakyTest = null;
                    break;
                case "stackTrace":
                    currentFlakyTest.addFailure(currentStackTrace.toString());
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

    public static class FlakyTest {

        private String className;
        private String methodName;
        private List<String> failures = new LinkedList<>();

        public FlakyTest(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }

        public void addFailure(String stackTrace) {
            this.failures.add(stackTrace);
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

    }

}
