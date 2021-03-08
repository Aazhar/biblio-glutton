package com.scienceminer.lookup.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rockymadden.stringmetric.similarity.RatcliffObershelpMetric;
import com.scienceminer.lookup.data.IstexData;
import com.scienceminer.lookup.data.MatchingDocument;
import com.scienceminer.lookup.data.PmidData;
import com.scienceminer.lookup.exception.NotFoundException;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.storage.lookup.*;
import com.scienceminer.lookup.utils.grobid.GrobidClient;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import scala.Option;

import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.*;

public class LookupEngine {

    private OALookup oaDoiLookup = null;

    private IstexIdsLookup istexLookup = null;

    private MetadataLookup metadataLookup = null;
    private MetadataMatching metadataMatching = null;
    private PMIdsLookup pmidLookup = null;
    public static Pattern DOIPattern = Pattern.compile("\"DOI\"\\s?:\\s?\"(10\\.\\d{4,5}\\/[^\"\\s]+[^;,.\\s])\"");
    private GrobidClient grobidClient = null;

    private DocumentBuilder builder;
    private XPath xPath;

    private static String ISTEX_BASE = "https://api.istex.fr/document/";

    public LookupEngine() {
    }

    public LookupEngine(StorageEnvFactory storageFactory) throws TransformerConfigurationException, ParserConfigurationException {
        this.oaDoiLookup = new OALookup(storageFactory);
        this.istexLookup = new IstexIdsLookup(storageFactory);
        this.metadataLookup = new MetadataLookup(storageFactory);
        this.metadataMatching = new MetadataMatching(storageFactory.getConfiguration(), metadataLookup);
        this.pmidLookup = new PMIdsLookup(storageFactory);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        this.builder = factory.newDocumentBuilder();
        XPathFactory xPathFactory = XPathFactory.newInstance();
        this.xPath = xPathFactory.newXPath();
    }


    public String retrieveByArticleMetadata(String title, String firstAuthor, Boolean postValidate, String mediaType) {
        MatchingDocument outputData = metadataMatching.retrieveByMetadata(title, firstAuthor);
        if (postValidate != null && postValidate) {
            if (!areMetadataMatching(title, firstAuthor, outputData)) {
                throw new NotFoundException("Best bibliographical record did not passed the post-validation");
            }
        }
        return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI(), mediaType);
    }

    public void retrieveByArticleMetadataAsync(String title, String firstAuthor, Boolean postValidate, Consumer<MatchingDocument> callback, String mediaType) {
        metadataMatching.retrieveByMetadataAsync(title, firstAuthor, matchingDocument -> {
            if (!matchingDocument.isException()) {
                if (postValidate != null && postValidate) {
                    if (!areMetadataMatching(title, firstAuthor, matchingDocument)) {
                        callback.accept(new MatchingDocument(new NotFoundException("Best bibliographical record did not passed the post-validation")));
                        return;
                    }
                }

                final String s = injectIdsByDoi(matchingDocument.getJsonObject(), matchingDocument.getDOI(), mediaType);
                matchingDocument.setFinalJsonObject(s);
            }
            callback.accept(matchingDocument);
        });
    }


    public String retrieveByJournalMetadata(String title, String volume, String firstPage, String mediaType) {
        MatchingDocument outputData = metadataMatching.retrieveByMetadata(title, volume, firstPage);
        return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI(), mediaType);
    }

    public void retrieveByJournalMetadataAsync(String jtitle, String volume, String firstPage, String atitle, String firstAuthor, Boolean postValidate, Consumer<MatchingDocument> callback, String mediaType) {
        metadataMatching.retrieveByMetadataAsync(jtitle, volume, firstPage, matchingDocument -> {
            if (!matchingDocument.isException()) {
                if (postValidate != null && postValidate) {
                    if (!areMetadataMatching(atitle, firstAuthor, matchingDocument, true)) {
                        callback.accept(new MatchingDocument(new NotFoundException("Best bibliographical record did not passed the post-validation")));
                        return;
                    }
                }

                final String s = injectIdsByDoi(matchingDocument.getJsonObject(), matchingDocument.getDOI(), mediaType);
                matchingDocument.setFinalJsonObject(s);
            }
            callback.accept(matchingDocument);
        });
    }

    public void retrieveByJournalMetadataAsync(String jtitle, String volume, String firstPage, Consumer<MatchingDocument> callback, String mediaType) {
        metadataMatching.retrieveByMetadataAsync(jtitle, volume, firstPage, matchingDocument -> {
            if (!matchingDocument.isException()) {
                final String s = injectIdsByDoi(matchingDocument.getJsonObject(), matchingDocument.getDOI(), mediaType);
                matchingDocument.setFinalJsonObject(s);
            }
            callback.accept(matchingDocument);
        });
    }

    public String retrieveByJournalMetadata(String title, String volume, String firstPage, String firstAuthor, String mediaType) {
        MatchingDocument outputData = metadataMatching.retrieveByMetadata(title, volume, firstPage, firstAuthor);
        return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI(), mediaType);
    }

    public void retrieveByJournalMetadataAsync(String title, String volume, String firstPage, String firstAuthor,
                                               Consumer<MatchingDocument> callback, String mediaType) {
        metadataMatching.retrieveByMetadataAsync(title, volume, firstPage, firstAuthor, matchingDocument -> {
            if (!matchingDocument.isException()) {
                final String s = injectIdsByDoi(matchingDocument.getJsonObject(), matchingDocument.getDOI(), mediaType);
                matchingDocument.setFinalJsonObject(s);
            }
            callback.accept(matchingDocument);
        });
    }

    public String retrieveByDoi(String doi, Boolean postValidate, String firstAuthor, String atitle, String mediaType) {
        MatchingDocument outputData = metadataLookup.retrieveByMetadata(doi);
        try {
            switch (mediaType) {
                case MediaType.APPLICATION_JSON: {
                    outputData = validateJsonBody(postValidate, firstAuthor, atitle, outputData);
                    break;
                }
                case MediaType.APPLICATION_XML:
                    outputData = validateXmlBody(postValidate, firstAuthor, atitle, outputData);
                    break;
            }

            return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI(), mediaType);
        }catch (IOException | XPathExpressionException | SAXException e) {
            throw new ServiceException(500, "Internal error.");
        }
    }

    private MatchingDocument validateJsonBody(Boolean postValidate, String firstAuthor, String atitle, MatchingDocument outputData) {
        if (isBlank(outputData.getJsonObject())) {
            throw new NotFoundException("No bibliographical record found");
        }

        if (postValidate != null && postValidate && isNotBlank(firstAuthor)) {
            outputData = extractTitleAndFirstAuthorFromJson(outputData);

            if (!areMetadataMatching(atitle, firstAuthor, outputData, true)) {
                throw new NotFoundException("Best bibliographical record did not passed the post-validation");
            }
        }
        return outputData;
    }

    private MatchingDocument validateXmlBody(Boolean postValidate, String firstAuthor, String atitle, MatchingDocument outputData) throws XPathExpressionException, SAXException, IOException {
        if (isBlank(outputData.getJsonObject())) {
            throw new NotFoundException("No bibliographical record found");
        }

        if (postValidate != null && postValidate && isNotBlank(firstAuthor)) {
            outputData = extractTitleAndFirstAuthorFromXml(outputData);

            if (!areMetadataMatching(atitle, firstAuthor, outputData, true)) {
                throw new NotFoundException("Best bibliographical record did not passed the post-validation");
            }
        }
        return outputData;
    }

    private MatchingDocument extractTitleAndFirstAuthorFromJson(MatchingDocument outputData) {
        JsonElement jelement = new JsonParser().parse(outputData.getJsonObject());
        JsonObject jobject = jelement.getAsJsonObject();

        final JsonArray titlesFromJson = jobject.get("title").getAsJsonArray();
        if (titlesFromJson != null && titlesFromJson.size() > 0) {
            String titleFromJson = titlesFromJson.get(0).getAsString();
            outputData.setTitle(titleFromJson);
        }

        final JsonArray authorsFromJson = jobject.get("author").getAsJsonArray();
        if (authorsFromJson != null && authorsFromJson.size() > 0) {

            String firstAuthorFromJson = "";
            for (int i = 0; i < authorsFromJson.size(); i++) {
                final JsonObject currentAuthor = authorsFromJson.get(i).getAsJsonObject();
                if (currentAuthor.has("sequence")
                        && StringUtils.equals(currentAuthor.get("sequence").getAsString(), "first")) {
                    firstAuthorFromJson = currentAuthor.get("family").getAsString();
                    outputData.setFirstAuthor(firstAuthorFromJson);
                    break;
                }
            }
        }

        return outputData;
    }

    private MatchingDocument extractTitleAndFirstAuthorFromXml(MatchingDocument outputData) throws IOException, SAXException, XPathExpressionException {
        final Document doc = builder.parse(new InputSource(new StringReader(outputData.getJsonObject())));
        Element titleElement = (Element) xPath.compile("/record/metadata/crossref_result/query_result/body/crossref_metadata/doi_record/crossref/*/*/titles/title[0]").evaluate(doc, XPathConstants.NODE);
        if (titleElement != null) {
            String title = titleElement.getTextContent();
            outputData.setTitle(title);
        }

        NodeList authorsList = (NodeList) xPath.compile("/record/metadata/crossref_result/query_result/body/crossref_metadata/doi_record/crossref/*/*/contributors/person_name").evaluate(doc, XPathConstants.NODESET);

        if (authorsList != null && authorsList.getLength() > 0) {

            String firstAuthor = "";
            for (int i = 0; i < authorsList.getLength(); i++) {
                final Node currentAuthor = authorsList.item(i);
                if (currentAuthor != null && currentAuthor.getNodeType() == Node.ELEMENT_NODE
                        && StringUtils.equals(((Element)currentAuthor).getAttribute("sequence"), "first")) {
                    firstAuthor = ((Element) currentAuthor).getElementsByTagName("surname").item(0).getTextContent();
                    outputData.setFirstAuthor(firstAuthor);
                    break;
                }
            }
        }

        return outputData;
    }

    public String retrieveByPmid(String pmid, Boolean postValidate, String firstAuthor, String atitle, String mediaType) {
        final PmidData pmidData = pmidLookup.retrieveIdsByPmid(pmid);

        if (pmidData != null && isNotBlank(pmidData.getDoi())) {
            return retrieveByDoi(pmidData.getDoi(), postValidate, firstAuthor, atitle, mediaType);
        }

        throw new NotFoundException("Cannot find bibliographical record with PMID " + pmid);
    }

    public String retrieveByPmc(String pmc, Boolean postValidate, String firstAuthor, String atitle, String mediaType) {
        if (!StringUtils.startsWithIgnoreCase(pmc, "pmc")) {
            pmc = "PMC" + pmc;
        }

        final PmidData pmidData = pmidLookup.retrieveIdsByPmc(pmc);

        if (pmidData != null && isNotBlank(pmidData.getDoi())) {
            return retrieveByDoi(pmidData.getDoi(), postValidate, firstAuthor, atitle, mediaType);
        }

        throw new NotFoundException("Cannot find bibliographical record with PMC ID " + pmc);
    }

    public String retrieveByIstexid(String istexid, Boolean postValidate, String firstAuthor, String atitle, String mediaType) {
        final IstexData istexData = istexLookup.retrieveByIstexId(istexid);

        if (istexData != null && CollectionUtils.isNotEmpty(istexData.getDoi()) && isNotBlank(istexData.getDoi().get(0))) {
            final String doi = istexData.getDoi().get(0);
            MatchingDocument outputData = metadataLookup.retrieveByMetadata(doi);

            final String oaLink = oaDoiLookup.retrieveOaLinkByDoi(doi);
            try {
                switch (mediaType) {
                    case MediaType.APPLICATION_JSON: {
                        outputData = validateJsonBody(postValidate, firstAuthor, atitle, outputData);
                        return injectIdsByIstexDataJson(outputData.getJsonObject(), doi, istexData, oaLink);
                    }
                    case MediaType.APPLICATION_XML:
                        outputData = validateXmlBody(postValidate, firstAuthor, atitle, outputData);
                        return injectIdsByIstexDataXml(outputData.getJsonObject(), doi, istexData, oaLink);
                }
            }catch (IOException | XPathExpressionException | SAXException e) {
                throw new ServiceException(500, "Internal error.");
            }
        }

        throw new NotFoundException("Cannot find bibliographical record with ISTEX ID " + istexid);
    }

    public String retrieveByPii(String pii, Boolean postValidate, String firstAuthor, String atitle, String mediaType) {
        final IstexData istexData = istexLookup.retrieveByPii(pii);

        if (istexData != null && CollectionUtils.isNotEmpty(istexData.getDoi()) && isNotBlank(istexData.getDoi().get(0))) {
            final String doi = istexData.getDoi().get(0);
            MatchingDocument outputData = metadataLookup.retrieveByMetadata(doi);

            final String oaLink = oaDoiLookup.retrieveOaLinkByDoi(doi);
            try {
                switch (mediaType) {
                    case MediaType.APPLICATION_JSON: {
                        outputData = validateJsonBody(postValidate, firstAuthor, atitle, outputData);
                        return injectIdsByIstexDataJson(outputData.getJsonObject(), doi, istexData, oaLink);
                    }
                    case MediaType.APPLICATION_XML:
                        outputData = validateXmlBody(postValidate, firstAuthor, atitle, outputData);
                        return injectIdsByIstexDataXml(outputData.getJsonObject(), doi, istexData, oaLink);
                }
            }catch (IOException | XPathExpressionException | SAXException e) {
                throw new ServiceException(500, "Internal error.");
            }
        }

        throw new NotFoundException("Cannot find bibliographical record by PII " + pii);
    }


    // Intermediate lookups

    public PmidData retrievePMidsByDoi(String doi) {
        return pmidLookup.retrieveIdsByDoi(doi);
    }

    public PmidData retrievePMidsByPmid(String pmid) {
        return pmidLookup.retrieveIdsByPmid(pmid);
    }

    public PmidData retrievePMidsByPmc(String pmc) {
        return pmidLookup.retrieveIdsByPmc(pmc);
    }

    public IstexData retrieveIstexIdsByDoi(String doi) {
        return istexLookup.retrieveByDoi(doi);
    }

    public IstexData retrieveIstexIdsByIstexId(String istexId) {
        return istexLookup.retrieveByIstexId(istexId);
    }

    public String retrieveOAUrlByDoi(String doi) {

        final String output = oaDoiLookup.retrieveOaLinkByDoi(doi);

        if (isBlank(output)) {
            throw new NotFoundException("Open Access URL was not found for DOI " + doi);
        }

        return output;
    }

    public Pair<String,String> retrieveOaIstexUrlByDoi(String doi) {

        final String oaLink = oaDoiLookup.retrieveOaLinkByDoi(doi);
        final IstexData istexRecord = istexLookup.retrieveByDoi(doi);
        String url = null;

        if (isBlank(oaLink) && istexRecord == null) {
            throw new NotFoundException("Open Access and Istex URL were not found for DOI " + doi);
        }        

        if (istexRecord != null) {
            String istexId = istexRecord.getIstexId();
            url = ISTEX_BASE + istexId + "/fulltext/pdf";
        }

        return Pair.of(oaLink, url);
    }

    public String retrieveOAUrlByPmid(String pmid) {
        final PmidData pmidData = pmidLookup.retrieveIdsByPmid(pmid);

        if (pmidData != null && isNotBlank(pmidData.getDoi())) {
            return oaDoiLookup.retrieveOaLinkByDoi(pmidData.getDoi());
        }

        throw new NotFoundException("Open Access URL was not found for PM ID " + pmid);
    }

    public Pair<String,String> retrieveOaIstexUrlByPmid(String pmid) {

        final PmidData pmidData = pmidLookup.retrieveIdsByPmid(pmid);

        if (pmidData == null || isBlank(pmidData.getDoi())) {
            throw new NotFoundException("Open Access and Istex URL were not found for PMID " + pmid);
        }        

        final String oaLink = oaDoiLookup.retrieveOaLinkByDoi(pmidData.getDoi());
        final IstexData istexRecord = istexLookup.retrieveByDoi(pmidData.getDoi());
        String url = null;

        if (isBlank(oaLink) && istexRecord == null) {
            throw new NotFoundException("Open Access and Istex URL were not found for PMID " + pmid);
        }        

        if (istexRecord != null) {
            String istexId = istexRecord.getIstexId();
            url = ISTEX_BASE + istexId + "/fulltext/pdf";
        }

        return Pair.of(oaLink, url);
    }

    public String retrieveOAUrlByPmc(String pmc) {
        final PmidData pmidData = pmidLookup.retrieveIdsByPmc(pmc);

        if (pmidData != null && isNotBlank(pmidData.getDoi())) {
            return oaDoiLookup.retrieveOaLinkByDoi(pmidData.getDoi());
        }

        throw new NotFoundException("Open Access URL was not found for PMC ID " + pmc);
    }

    public Pair<String,String> retrieveOaIstexUrlByPmc(String pmc) {

        final PmidData pmidData = pmidLookup.retrieveIdsByPmc(pmc);

        if (pmidData == null || isBlank(pmidData.getDoi())) {
            throw new NotFoundException("Open Access and Istex URL were not found for PMC " + pmc);
        }        

        final String oaLink = oaDoiLookup.retrieveOaLinkByDoi(pmidData.getDoi());
        final IstexData istexRecord = istexLookup.retrieveByDoi(pmidData.getDoi());
        String url = null;

        if (isBlank(oaLink) && istexRecord == null) {
            throw new NotFoundException("Open Access and Istex URL were not found for PMC " + pmc);
        }        

        if (istexRecord != null) {
            String istexId = istexRecord.getIstexId();
            url = ISTEX_BASE + istexId + "/fulltext/pdf";
        }

        return Pair.of(oaLink, url);
    }

    public String retrieveOAUrlByPii(String pii) {
        final IstexData istexData = istexLookup.retrieveByPii(pii);

        if (istexData != null && CollectionUtils.isNotEmpty(istexData.getDoi())) {
            // TBD: we might want to iterate to several DOI
            return oaDoiLookup.retrieveOaLinkByDoi(istexData.getDoi().get(0));
        }

        throw new NotFoundException("Open Access URL was not found for pii " + pii);
    }

    public Pair<String,String> retrieveOaIstexUrlByPii(String pii) {

        final IstexData istexData = istexLookup.retrieveByPii(pii);

        if (istexData == null || istexData.getDoi() == null || istexData.getDoi().size() == 0) {
            throw new NotFoundException("Open Access and Istex URL were not found for PII " + pii);
        }        

        // TBD: we might want to iterate to several DOI
        final String oaLink = oaDoiLookup.retrieveOaLinkByDoi(istexData.getDoi().get(0));
        String istexId = istexData.getIstexId();
        String url = ISTEX_BASE + istexId + "/fulltext/pdf";

        return Pair.of(oaLink, url);
    }

    public String retrieveByBiblio(String biblio, String mediaType) {
        final MatchingDocument outputData = metadataMatching.retrieveByBiblio(biblio);
        return injectIdsByDoi(outputData.getJsonObject(), outputData.getDOI(), mediaType);
    }

    public void retrieveByBiblioAsync(String biblio, Boolean postValidate, String firstAuthor, String title, Boolean parseReference, Consumer<MatchingDocument> callback, String mediaType) {
        metadataMatching.retrieveByBiblioAsync(biblio, matchingDocument -> {
            if (!matchingDocument.isException()) {
                if (postValidate != null && postValidate) {
                    //no title and author, extract with grobid. if grobid unavailable... it will fail.
                    if (isBlank(firstAuthor) && parseReference) {
                        try {
                            grobidClient.ping();
                            grobidClient.processCitation(biblio, "0", response -> {
                                final String firstAuthor1 = isNotBlank(response.getFirstAuthor()) ? response.getFirstAuthor() : response.getFirstAuthorMonograph();
                                if (!areMetadataMatching(response.getAtitle(), firstAuthor1, matchingDocument, true)) {
                                    callback.accept(new MatchingDocument(new NotFoundException("Best bibliographical record did not passed the post-validation")));
                                    return;
                                }
                                final String s = injectIdsByDoi(matchingDocument.getJsonObject(), matchingDocument.getDOI(), mediaType);
                                matchingDocument.setFinalJsonObject(s);
                                callback.accept(matchingDocument);
                            });
                        } catch (Exception e) {
                            callback.accept(new MatchingDocument(new NotFoundException("Post-validation not possible, no title/first author provided for validation and " +
                                    "GROBID is not available.", e)));
                        }
                    } else {
                        if (!areMetadataMatching(title, firstAuthor, matchingDocument, true)) {
                            callback.accept(new MatchingDocument(new NotFoundException("Best bibliographical record did not passed the post-validation")));
                            return;
                        }
                        final String s = injectIdsByDoi(matchingDocument.getJsonObject(), matchingDocument.getDOI(), mediaType);
                        matchingDocument.setFinalJsonObject(s);
                        callback.accept(matchingDocument);
                    }
                } else {
                    final String s = injectIdsByDoi(matchingDocument.getJsonObject(), matchingDocument.getDOI(), mediaType);
                    matchingDocument.setFinalJsonObject(s);
                    callback.accept(matchingDocument);
                }
            } else {
                // got an exception; propagate it
                callback.accept(matchingDocument);
            }
        });
    }

    public void retrieveByBiblioAsync(String biblio, Consumer<MatchingDocument> callback, String mediaType) {
        metadataMatching.retrieveByBiblioAsync(biblio, matchingDocument -> {
            if (!matchingDocument.isException()) {
                final String s = injectIdsByDoi(matchingDocument.getJsonObject(), matchingDocument.getDOI(), mediaType);
                matchingDocument.setFinalJsonObject(s);
            }
            callback.accept(matchingDocument);
        });
    }

    public String fetchDOI(String input) {
        //From grobid
//        Pattern DOIPattern = Pattern.compile("(10\\.\\d{4,5}\\/[\\S]+[^;,.\\s])");

        Matcher doiMatcher = DOIPattern.matcher(input);
        while (doiMatcher.find()) {
            if (doiMatcher.groupCount() == 1) {
                return doiMatcher.group(1);
            }
        }

        return null;
    }

    /**
     * Methods borrowed from GROBID Consolidation.java
     */

    /**
     * The bibliographical matching service is a search API, and thus returns
     * many false positives. It is necessary to validate return results
     * against the (incomplete) source bibliographic item to block
     * inconsistent results.
     */
    private boolean areMetadataMatching(String title, String firstAuthor, MatchingDocument result, boolean ignoreTitleIfNotPresent) {
        boolean valid = true;


        if (isNotBlank(title)) {
            if (ratcliffObershelpDistance(title, result.getTitle(), false) < 0.7)
                return false;
        } else if (!ignoreTitleIfNotPresent) {
            return false;
        }


        if (ratcliffObershelpDistance(firstAuthor, result.getFirstAuthor(), false) < 0.7)
            return false;

        return valid;
    }

    /**
     * default version checking title and authors
     **/
    private boolean areMetadataMatching(String title, String firstAuthor, MatchingDocument result) {
        return areMetadataMatching(title, firstAuthor, result, false);
    }


    private double ratcliffObershelpDistance(String string1, String string2, boolean caseDependent) {
        if (StringUtils.isBlank(string1) || StringUtils.isBlank(string2))
            return 0.0;
        Double similarity = 0.0;

        if (!caseDependent) {
            string1 = string1.toLowerCase();
            string2 = string2.toLowerCase();
        }

        if (string1.equals(string2))
            similarity = 1.0;
        if ((string1.length() > 0) && (string2.length() > 0)) {
            Option<Object> similarityObject =
                    RatcliffObershelpMetric.compare(string1, string2);
            if ((similarityObject != null) && (similarityObject.get() != null))
                similarity = (Double) similarityObject.get();
        }

        return similarity;
    }


    protected String injectIdsByDoi(String jsonobj, String doi, String mediaType) {
        final IstexData istexData = istexLookup.retrieveByDoi(doi);

        final String oaLink = oaDoiLookup.retrieveOaLinkByDoi(doi);
        switch(mediaType){
            case MediaType.APPLICATION_JSON: {
                return injectIdsByIstexDataJson(jsonobj, doi, istexData, oaLink);
            }
            case MediaType.APPLICATION_XML:
                return injectIdsByIstexDataXml(jsonobj, doi, istexData, oaLink);
            default:
                throw new ServiceException(400, "provided format is either xml or json");
        }
    }

    protected String injectIdsByIstexDataXml(String jsonobj, String doi, IstexData istexData, String oaLink) {
        boolean pmid = false;
        boolean pmc = false;
        boolean foundIstexData = false;
        boolean foundPmidData = false;
        boolean first = false;
        boolean foundOaLink = false;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><result>");
        sb.append(jsonobj);
        sb.append("<external_identifiers>");
        if (istexData != null) {
            if (isNotBlank(istexData.getIstexId())) {
                sb.append("<external_identifier type=\"istexId\">" + istexData.getIstexId() + "</external_identifier>");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getArk())) {
                sb.append("<external_identifier type=\"ark\">" + istexData.getArk().get(0) + "</external_identifier>");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPmid())) {
                sb.append("<external_identifier type=\"pmid\">" + istexData.getPmid().get(0) + "</external_identifier>");
                pmid = true;
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPmc())) {
                sb.append("<external_identifier type=\"pmcid\">" + istexData.getPmc().get(0) + "</external_identifier>");
                pmc = true;
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getMesh())) {
                sb.append("<external_identifier type=\"mesh\">" + istexData.getMesh().get(0) + "</external_identifier>");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPii())) {
                sb.append("<external_identifier type=\"pii\">" + istexData.getPii().get(0)  + "</external_identifier>");
                foundIstexData = true;
            }
        }

        if (!pmid || !pmc) {
            final PmidData pmidData = pmidLookup.retrieveIdsByDoi(doi);
            if (pmidData != null) {
                if (isNotBlank(pmidData.getPmid()) && !pmid) {
                    sb.append("<external_identifier type=\"pmid\">" + pmidData.getPmid()  + "</external_identifier>");
                    foundPmidData = true;
                }

                if (isNotBlank(pmidData.getPmcid()) && !pmc) {
                    sb.append("<external_identifier type=\"pmcid\">" + pmidData.getPmcid()  + "</external_identifier>");
                    foundPmidData = true;
                }
            }
        }

        if (isNotBlank(oaLink)) {
            sb.append("<external_identifier type=\"oaLink\">" + oaLink  + "</external_identifier>");
            foundOaLink = true;

        }
        sb.append("</external_identifiers>");
        sb.append("</result>");
        return sb.toString();
    }

    protected String injectIdsByIstexDataJson(String jsonobj, String doi, IstexData istexData, String oaLink) {
        boolean pmid = false;
        boolean pmc = false;
        boolean foundIstexData = false;
        boolean foundPmidData = false;
        boolean first = false;
        boolean foundOaLink = false;

        StringBuilder sb = new StringBuilder();
        if (isBlank(jsonobj)) {
            sb.append("{");
            first = true;
        } else {
            sb.append(jsonobj, 0, length(jsonobj) - 1);
        }

        if (istexData != null) {
            if (isNotBlank(istexData.getIstexId())) {
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append("\"istexId\":\"" + istexData.getIstexId() + "\"");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getArk())) {
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append("\"ark\":\"" + istexData.getArk().get(0) + "\"");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPmid())) {
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append("\"pmid\":\"" + istexData.getPmid().get(0) + "\"");
                pmid = true;
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPmc())) {
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append("\"pmcid\":\"" + istexData.getPmc().get(0) + "\"");
                pmc = true;
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getMesh())) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append("\"mesh\":\"" + istexData.getMesh().get(0) + "\"");
                foundIstexData = true;
            }
            if (CollectionUtils.isNotEmpty(istexData.getPii())) {
                if (!first) {
                    sb.append(", ");
                } else {
                    first = false;
                }
                sb.append("\"pii\":\"" + istexData.getPii().get(0) + "\"");
                foundIstexData = true;
            }
        }

        if (!pmid || !pmc) {
            final PmidData pmidData = pmidLookup.retrieveIdsByDoi(doi);
            if (pmidData != null) {
                if (isNotBlank(pmidData.getPmid()) && !pmid) {
                    if (!first) {
                        sb.append(", ");
                    } else {
                        first = false;
                    }
                    sb.append("\"pmid\":\"" + pmidData.getPmid() + "\"");
                    foundPmidData = true;
                }

                if (isNotBlank(pmidData.getPmcid()) && !pmc) {
                    if (!first) {
                        sb.append(", ");
                    } else {
                        first = false;
                    }
                    sb.append("\"pmcid\":\"" + pmidData.getPmcid() + "\"");
                    foundPmidData = true;
                }
            }
        }

        if (isNotBlank(oaLink)) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }
            sb.append("\"oaLink\":\"" + oaLink + "\"");
            foundOaLink = true;

        }

        if (foundIstexData || foundPmidData || foundOaLink) {
            sb.append("}");
            return sb.toString();
        } else {
            return jsonobj;
        }
    }

    public void setMetadataMatching(MetadataMatching metadataMatching) {
        this.metadataMatching = metadataMatching;
    }

    public void setOaDoiLookup(OALookup oaDoiLookup) {
        this.oaDoiLookup = oaDoiLookup;
    }

    public void setIstexLookup(IstexIdsLookup istexLookup) {
        this.istexLookup = istexLookup;
    }

    public void setMetadataLookup(MetadataLookup metadataLookup) {
        this.metadataLookup = metadataLookup;
    }

    public void setPmidLookup(PMIdsLookup pmidLookup) {
        this.pmidLookup = pmidLookup;
    }

    public void setGrobidClient(GrobidClient grobidClient) {
        this.grobidClient = grobidClient;
    }
}
