package de.tum.ftm.agentsim.ts.utils;

import org.pmw.tinylog.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;

/**
 * Helper class to process XML files
 *
 * @author Michael Wittmann, Manfred Klöppel
 */

public class UtilXML {

    /**
     * Validate a XML-file against a XSD-file
     *
     * @param xsdPath Path to the XSD-file
     * @param xmlPath Path to the XML-file which should be validated
     * @return true, if XML-File is valid, else false
     */
    public static boolean validateXMLSchema(String xsdPath, String xmlPath) {

        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new File(xsdPath));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new File(xmlPath)));
        } catch (IOException | SAXException e) {
            Logger.error(e.getMessage());
            Logger.error("XML File Validation Error");
            return false;
        }
        Logger.info("XML File OK");
        return true;
    }

    /**
     * I take a xml element and the tag name, look for the tag and get
     * the text content
     * i.e for <employee><name>John</name></employee> xml snippet if
     * the Element points to employee node and tagName is 'name' I will return John
     */
    public static String getString(Element ele, String tagName) {
        NodeList nl = ele.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            Element el = (Element) nl.item(0);
            return el.getFirstChild().getNodeValue();
        } else {
            throw new RuntimeException("Element " + tagName + " not found");
        }
    }

    /**
     * I take a xml element and the tag name, look for the tag and get
     * the element
     */
    public static Element getElement(Element ele, String tagName) {

        NodeList nl = ele.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            return (Element) nl.item(0);

        } else {
            throw new RuntimeException("Element " + tagName + " not found");
        }
    }

    /**
     * @param base Base Element from which the Child value should be returned as string
     * @param name The searched element
     * @return Child element as String
     */
    public static String getChildStringValueForElement(final Element base, final String name) {
        String value = null;
        NodeList nodeList = base.getChildNodes();
        if (nodeList.getLength() > 0) {
            int length = nodeList.getLength();
            for (int i = 0; i < length; i++) {
                Node node = nodeList.item(i);

                // Get an element, create an instance of the element
                if (Node.ELEMENT_NODE == node.getNodeType()) {
                    if (name.equals(node.getNodeName())) {
                        // Get value of this child
                        Node elNode = ((Element) node).getFirstChild();
                        if (elNode != null) {
                            value = elNode.getNodeValue();
                            break;
                        }
                    }
                }
            }
        }
        return value;
    }
}
