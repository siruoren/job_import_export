package com.siruoren.jobimportexport.service;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class SecureXmlParser {

    private static final String FEATURE_DISALLOW_DOCTYPE = 
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_EXTERNAL_GENERAL = 
            "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER = 
            "http://xml.org/sax/features/external-parameter-entities";

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
}