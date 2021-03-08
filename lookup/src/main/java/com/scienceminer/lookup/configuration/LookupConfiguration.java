package com.scienceminer.lookup.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.client.HttpClientConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

public class LookupConfiguration extends Configuration {

    private int batchSize;

    private String storage;

    private String version;

    private Source source;

    private Elastic elastic;

    private String grobidPath;

    public Crossref getCrossref() {
        return crossref;
    }

    private Crossref crossref;

    @Valid
    @NotNull
    private HttpClientConfiguration httpClient = new HttpClientConfiguration();

    @JsonProperty("httpClient")
    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClient;
    }

    @JsonProperty("httpClient")
    public void setHttpClientConfiguration(HttpClientConfiguration httpClient) {
        this.httpClient = httpClient;
    }

    private List<String> ignoreCrossRefJsonFields;

    private List<String> ignoreCrossRefXmlTags;

    private int maxAcceptedRequests;

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Source getSource() {
        return source;
    }

    public Elastic getElastic() {
        return elastic;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public List<String> getIgnoreCrossRefJsonFields() {
        return this.ignoreCrossRefJsonFields;
    }

    public void setIgnoreCrossRefJsonFields(List<String> ignoreCrossRefJsonFields) {
        this.ignoreCrossRefJsonFields = ignoreCrossRefJsonFields;
    }

    public List<String> getIgnoreCrossRefXmlTags() {
        return this.ignoreCrossRefXmlTags;
    }

    public void setIgnoreCrossRefXmlTags(List<String> ignoreCrossRefXmlTags) {
        this.ignoreCrossRefXmlTags = ignoreCrossRefXmlTags;
    }

    public int getMaxAcceptedRequests() {
        final int maxAcceptedRequestsNormalised = maxAcceptedRequests < 1 ? Runtime.getRuntime().availableProcessors() : this.maxAcceptedRequests;
        return maxAcceptedRequestsNormalised;
    }

    public void setMaxAcceptedRequests(int maxAcceptedRequests) {
        this.maxAcceptedRequests = maxAcceptedRequests;
    }

    public String getGrobidPath() {
        return grobidPath;
    }

    public void setGrobidPath(String grobidPath) {
        this.grobidPath = grobidPath;
    }

    public class Source {

        private String unpaywall;
        private String istex;

        public String getUnpaywall() {
            return unpaywall;
        }

        public void setUnpaywall(String unpaywall) {
            this.unpaywall = unpaywall;
        }

        public String getIstex() {
            return istex;
        }

        public void setIstex(String istex) {
            this.istex = istex;
        }
    }
    
    public class Elastic {

        private String host;
        private String index;

        private String type;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

    }

    public class Crossref {

        private String host;
        private String rows;
        private String mailTo;


        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getRows() {
            return Integer.parseInt(rows);
        }

        public void setRows(String rows) {
            this.rows = rows;
        }

        public String getMailTo() {
            return mailTo;
        }

        public void setMailTo(String mailTo) {
            this.mailTo = mailTo;
        }

    }
}
