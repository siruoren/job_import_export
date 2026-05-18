package com.siruoren.jobimportexport.service;

import hudson.model.Descriptor;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.FreeStyleProject;
import jenkins.model.Jenkins;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SecureXmlParser {

    private static final Logger LOGGER = Logger.getLogger(SecureXmlParser.class.getName());

    private static final String FEATURE_DISALLOW_DOCTYPE =
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_EXTERNAL_GENERAL =
            "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER =
            "http://xml.org/sax/features/external-parameter-entities";

    private static final List<String> WEBHOOK_ELEMENT_PATTERNS = Arrays.asList(
            "GithubProjectProperty",
            "GitLabPushTrigger",
            "GitLabWebHookTrigger",
            "BitBucketTrigger",
            "BitbucketPushTrigger",
            "BitbucketBuildTrigger",
            "GhprbTrigger",
            "ScmRevisionStatus",
            "WebhookBuildTrigger",
            "GitHubPushTrigger",
            "GiteePushTrigger",
            "GogsTrigger",
            "PollingTrigger",
            "PushNotificationTrigger"
    );

    public static DocumentBuilder newSafeBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
        dbf.setFeature(FEATURE_EXTERNAL_GENERAL, false);
        dbf.setFeature(FEATURE_EXTERNAL_PARAMETER, false);
        dbf.setExpandEntityReferences(false);
        dbf.setXIncludeAware(false);
        dbf.setNamespaceAware(true);

        return dbf.newDocumentBuilder();
    }

    public static TopLevelItemDescriptor determineJobDescriptor(byte[] xmlBytes) throws Exception {
        byte[] cleaned = stripControlChars(xmlBytes);
        Document doc = newSafeBuilder().parse(new ByteArrayInputStream(cleaned));
        String rootElement = doc.getDocumentElement().getNodeName();

        TopLevelItemDescriptor descriptor = findDescriptorByRootElement(rootElement);
        if (descriptor == null) {
            LOGGER.log(Level.WARNING, "Cannot determine job descriptor for root element: {0}", rootElement);
        }
        return descriptor;
    }

    public static byte[] sanitizeJobConfig(byte[] xmlBytes) throws Exception {
        byte[] cleaned = stripControlChars(xmlBytes);
        Document doc = newSafeBuilder().parse(new ByteArrayInputStream(cleaned));
        Element root = doc.getDocumentElement();

        removeTriggers(root);
        removeWebhookProperties(root);
        setDisabled(root, doc);

        LOGGER.log(Level.INFO, "Job config sanitized: triggers and webhooks removed, job disabled");
        return documentToBytes(doc);
    }

    public static byte[] sanitizeJobConfigNoDisable(byte[] xmlBytes) throws Exception {
        byte[] cleaned = stripControlChars(xmlBytes);
        Document doc = newSafeBuilder().parse(new ByteArrayInputStream(cleaned));
        Element root = doc.getDocumentElement();

        removeTriggers(root);
        removeWebhookProperties(root);

        return documentToBytes(doc);
    }

    public static InputStream cleanXmlStream(byte[] xmlBytes) {
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        xml = xml.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] stripControlChars(byte[] xmlBytes) {
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        xml = xml.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static TopLevelItemDescriptor findDescriptorByRootElement(String rootElement) {
        Jenkins jenkins = Jenkins.get();

        switch (rootElement) {
            case "project":
                return jenkins.getDescriptorByType(FreeStyleProject.DescriptorImpl.class);
            case "flow-definition":
                return findDescriptorByClassName("org.jenkinsci.plugins.workflow.job.WorkflowJob");
            case "maven2-moduleset":
                return findDescriptorByClassName("hudson.maven.MavenModuleSet");
            case "matrix-project":
                return findDescriptorByClassName("hudson.matrix.MatrixProject");
            case "com.cloudbees.hudson.plugins.folder.Folder":
                return findDescriptorByClassName("com.cloudbees.hudson.plugins.folder.Folder");
            default:
                return findDescriptorByClassName(rootElement);
        }
    }

    private static TopLevelItemDescriptor findDescriptorByClassName(String className) {
        for (Descriptor<TopLevelItem> desc : Jenkins.get().getDescriptorList(TopLevelItem.class)) {
            if (desc instanceof TopLevelItemDescriptor && desc.clazz.getName().equals(className)) {
                return (TopLevelItemDescriptor) desc;
            }
        }
        return null;
    }

    private static void removeTriggers(Element root) {
        NodeList triggersList = root.getElementsByTagName("triggers");
        for (int i = triggersList.getLength() - 1; i >= 0; i--) {
            Node triggers = triggersList.item(i);
            Node parent = triggers.getParentNode();
            if (parent != null) {
                parent.removeChild(triggers);
                LOGGER.log(Level.FINE, "Removed <triggers> element from job config");
            }
        }
    }

    private static void removeWebhookProperties(Element root) {
        NodeList propertiesList = root.getElementsByTagName("properties");
        for (int i = 0; i < propertiesList.getLength(); i++) {
            Element properties = (Element) propertiesList.item(i);
            removeWebhookChildren(properties);
        }

        NodeList publishersList = root.getElementsByTagName("publishers");
        for (int i = 0; i < publishersList.getLength(); i++) {
            Element publishers = (Element) publishersList.item(i);
            removeWebhookChildren(publishers);
        }
    }

    private static void removeWebhookChildren(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element childElem = (Element) child;
                String nodeName = childElem.getNodeName();
                if (isWebhookElement(nodeName)) {
                    parent.removeChild(child);
                    LOGGER.log(Level.FINE, "Removed webhook element: {0}", nodeName);
                }
            }
        }
    }

    private static boolean isWebhookElement(String elementName) {
        for (String pattern : WEBHOOK_ELEMENT_PATTERNS) {
            if (elementName.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static void setDisabled(Element root, Document doc) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "disabled".equals(child.getNodeName())) {
                child.setTextContent("true");
                return;
            }
        }

        Element disabled = doc.createElement("disabled");
        disabled.setTextContent("true");
        root.appendChild(disabled);
    }

    private static byte[] documentToBytes(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(baos));
        return baos.toByteArray();
    }
}
