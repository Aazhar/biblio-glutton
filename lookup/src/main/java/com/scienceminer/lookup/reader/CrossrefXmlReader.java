package com.scienceminer.lookup.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.utils.BinarySerialiser;
import com.scienceminer.lookup.utils.Compressors;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class CrossrefXmlReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrossrefXmlReader.class);
    private LookupConfiguration configuration;
    private DocumentBuilder builder;
    private XPath xPath;

    private Transformer transformer;

    public CrossrefXmlReader(LookupConfiguration configuration) throws ParserConfigurationException, TransformerConfigurationException {
        this.configuration = configuration;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        this.builder = factory.newDocumentBuilder();
        XPathFactory xPathFactory = XPathFactory.newInstance();
        this.xPath = xPathFactory.newXPath();


        TransformerFactory transformerFactory = TransformerFactory
                .newInstance();
        this.transformer = transformerFactory.newTransformer();
        this.transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    }

    public void loadFromXml(String inputLine, Consumer<Pair<String, String>> closure, boolean isAPI) throws IOException, SAXException, XPathExpressionException, TransformerException {
        final Document doc = builder.parse(new InputSource(new StringReader(inputLine)));
        processFromXml(doc, closure, isAPI);
    }

    public void processFromXml(Document doc, Consumer<Pair<String, String>> closure, boolean isAPI) throws XPathExpressionException, IOException, TransformerException {
        /*if(isAPI){
            if(json.get("message") != null && json.get("status").asText().equals("ok")){
                JsonNode message = json.get("message");
                processItems(message, closure);
            }
        }else*/
            processXMLItems(doc, closure);
    }

    private void processXMLItems(Document doc, Consumer<Pair<String, String>> closure) throws XPathExpressionException, IOException, TransformerException {
        XPathExpression expr = xPath.compile("/OAI-PMH/ListRecords/record");
        NodeList listRecords = (NodeList) expr.evaluate(doc , XPathConstants.NODESET);
        for (int i = 0; i < listRecords.getLength(); i++) {
            Node node = listRecords.item(i);
            if ((node instanceof Element)) {
                Element record = (Element) listRecords.item(i);
                if(record.getTagName().equals("record")){
                    processXMLItem(record, closure);
                }
            }
        }
    }

    private void processXMLItem(Element record, Consumer<Pair<String, String>> closure) throws XPathExpressionException, IOException, TransformerException {
        if (record == null) {
            return;
        }

        Element doiElement = (Element) xPath.compile("metadata/crossref_result/query_result/body/crossref_metadata/doi").evaluate(record, XPathConstants.NODE);
        String doi = doiElement.getTextContent();
        //Ignoring empty DOI
        if (doi == null || isBlank(doi)) {
            return;
        }
        //Ignoring document of type component
        Element isComponentType = (Element) xPath.compile("metadata/crossref_result/query_result/body/crossref_metadata/doi_record/crossref/sa_component").evaluate(record, XPathConstants.NODE);
        Element isDatabaseType = (Element) xPath.compile("metadata/crossref_result/query_result/body/crossref_metadata/doi_record/crossref/database").evaluate(record, XPathConstants.NODE);
        Element isStandardType = (Element) xPath.compile("metadata/crossref_result/query_result/body/crossref_metadata/doi_record/crossref/standard").evaluate(record, XPathConstants.NODE);
        Element isErrorType = (Element) xPath.compile("metadata/crossref_result/query_result/body/crossref_metadata/doi_record/crossref/error").evaluate(record, XPathConstants.NODE);

        if (isComponentType != null || isDatabaseType != null || isStandardType != null|| isErrorType != null) {
            return;
        }
        //remove any unnecessary fields , check schema..
        if (configuration != null && configuration.getIgnoreCrossRefXmlTags() != null) {
            List<Node> nodesToDelete = new ArrayList<Node>();
            for (String tag : configuration.getIgnoreCrossRefXmlTags()) {
                NodeList nodelistToRemove = record.getElementsByTagName(tag);
                for (int i = 0; i < nodelistToRemove.getLength(); i++) {
                    Node child = nodelistToRemove.item(i);
                    if (child != null && child.getNodeType() == Node.ELEMENT_NODE) {
                        nodesToDelete.add(child);
                    }
                }
            }
            for (Node nodeToDelete: nodesToDelete) {
                nodeToDelete.getParentNode().removeChild( nodeToDelete );
            }
        }
        StringWriter writer = new StringWriter();
        this.transformer.transform(new DOMSource(record), new StreamResult(writer));
        closure.accept(new ImmutablePair<String, String>(doi, writer.toString()));
    }
}
