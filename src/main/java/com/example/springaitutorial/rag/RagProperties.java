package com.example.springaitutorial.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private String opensearchUrl;
    private String opensearchIndex;
    private double minRetrievalScore = 0.2;
    private int hybridCandidateTopK = 20;
    private int hybridRrfK = 60;
    private String rerankerApiUrl;
    private String rerankerApiKey;
    private String rerankerModel;
    private int rerankerTimeoutSeconds = 30;
    private double minRerankScore = 0.5;
    private String uploadDirectory = "data/uploads";
    private long uploadMaxBytes = 20 * 1024 * 1024;

    public String getOpensearchUrl() { return opensearchUrl; }
    public void setOpensearchUrl(String opensearchUrl) { this.opensearchUrl = opensearchUrl; }
    public String getOpensearchIndex() { return opensearchIndex; }
    public void setOpensearchIndex(String opensearchIndex) { this.opensearchIndex = opensearchIndex; }
    public double getMinRetrievalScore() { return minRetrievalScore; }
    public void setMinRetrievalScore(double value) { this.minRetrievalScore = value; }
    public int getHybridCandidateTopK() { return hybridCandidateTopK; }
    public void setHybridCandidateTopK(int value) { this.hybridCandidateTopK = value; }
    public int getHybridRrfK() { return hybridRrfK; }
    public void setHybridRrfK(int value) { this.hybridRrfK = value; }
    public String getRerankerApiUrl() { return rerankerApiUrl; }
    public void setRerankerApiUrl(String value) { this.rerankerApiUrl = value; }
    public String getRerankerApiKey() { return rerankerApiKey; }
    public void setRerankerApiKey(String value) { this.rerankerApiKey = value; }
    public String getRerankerModel() { return rerankerModel; }
    public void setRerankerModel(String value) { this.rerankerModel = value; }
    public int getRerankerTimeoutSeconds() { return rerankerTimeoutSeconds; }
    public void setRerankerTimeoutSeconds(int value) { this.rerankerTimeoutSeconds = value; }
    public double getMinRerankScore() { return minRerankScore; }
    public void setMinRerankScore(double value) { this.minRerankScore = value; }
    public String getUploadDirectory() { return uploadDirectory; }
    public void setUploadDirectory(String value) { this.uploadDirectory = value; }
    public long getUploadMaxBytes() { return uploadMaxBytes; }
    public void setUploadMaxBytes(long value) { this.uploadMaxBytes = value; }
}
