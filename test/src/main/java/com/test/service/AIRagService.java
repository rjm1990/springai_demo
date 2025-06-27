package com.test.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class AIRagService {
    private static final Logger log = LoggerFactory.getLogger(AIRagService.class);
    // 引入 system prompt tmpl
    @Value("classpath:/prompts.system-qa.st")
    private Resource systemResource;
    @Value("classpath:/a.pdf")
    private Resource pdf;

    // 注入相关 bean 实例
    private final ChatModel ragChatModel;

    private final RedisVectorStore vectorStore;

    @Autowired
    public AIRagService(RedisVectorStore redisVectorStore,ChatModel ragChatModel) {
        this.vectorStore = redisVectorStore;
        this.ragChatModel = ragChatModel;
    }
    // 文本过滤，增强向量检索精度
    private static final String textField = "content";

    /**
     * 获取 prompt 模板
     *
     * @param resource
     * @return
     */
    private String getPromptTemplate(Resource resource) {
        StringBuilder content = new StringBuilder();
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return content.toString();
    }
    public String search(String prompt) {

        // 加载 prompt tmpl
        String promptTemplate = getPromptTemplate(systemResource);

        // 启用混合搜索，包括嵌入和全文搜索
        SearchRequest searchRequest = SearchRequest.builder().
                topK(4)
                .similarityThresholdAll()
                .build();

        // build chatClient，发起大模型服务调用。
        String content = ChatClient.builder(ragChatModel)
                .build()
                .prompt()
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(searchRequest)
//                        .promptTemplate(promptTemplate)
                                .build()
                ).user(prompt)
                .call().content();
        return content;
    }

    public void add() {
        List<Resource> pdfResources = List.of(pdf);
        List<Document> documents = parsePdfResource(pdfResources);
        this.vectorStore.add(documents);
        log.info("add done.");
    }

    private List<Document> parsePdfResource(List<Resource> pdfResources) {
        List<Document> resList = new ArrayList<>();
        // 按照指定策略切分文本并转为 Document 资源对象
        for (Resource springAiResource : pdfResources) {

            // 1. parse document
            DocumentReader reader = new PagePdfDocumentReader(springAiResource);
            List<Document> documents = reader.get();

            // 2. split trunks
            List<Document> splitDocuments = new TokenTextSplitter().apply(documents);

            // 3. add res list
            resList.addAll(splitDocuments);
        }
        return resList;
    }
}
