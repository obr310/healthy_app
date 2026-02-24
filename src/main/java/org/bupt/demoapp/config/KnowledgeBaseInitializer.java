package org.bupt.demoapp.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库初始化器
 *
 * 应用启动时自动加载 content/ 目录下的医学知识库文档，
 * 按段落切分后存入 knowledge_base_vectors collection
 */
@Component
public class KnowledgeBaseInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);

    // 记录已向量化文件的本地文件路径
    private static final String VECTORIZED_FILES_RECORD = "data/vectorized_files.txt";

    @Autowired
    @Qualifier("knowledgeBaseEmbeddingStore")
    private MilvusEmbeddingStore knowledgeBaseEmbeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Override
    public void run(String... args) throws Exception {
        log.info(">>> 开始初始化知识库...");

        try {
            // 加载 content/ 目录下的所有文档
            List<Document> documents = ClassPathDocumentLoader.loadDocuments("content");
            log.info(">>> 加载知识库文档: {} 个文件", documents.size());

            if (documents.isEmpty()) {
                log.warn(">>> 未找到知识库文档，跳过初始化");
                return;
            }

            // 从本地文件读取已向量化的文档列表
            Set<String> vectorizedFiles = loadVectorizedFiles();
            log.info(">>> 已向量化的文档: {}", vectorizedFiles);

            // 文档切分器：按段落切分，每个段落最多 1000 字符，重叠 200 字符
            DocumentSplitter splitter = new DocumentByParagraphSplitter(1000, 200);

            int totalSegments = 0;
            int skippedFiles = 0;
            long startTime = System.currentTimeMillis();

            for (Document doc : documents) {
                String fileName = doc.metadata().getString("file_name");

                // 检查文档是否已经向量化过
                if (vectorizedFiles.contains(fileName)) {
                    log.info(">>> 跳过已向量化的文档: {}", fileName);
                    skippedFiles++;
                    continue;
                }

                List<TextSegment> segments = splitter.split(doc);
                log.info(">>> 处理文档: {}, 切分为 {} 个片段", fileName, segments.size());

                int segmentCount = 0;
                for (TextSegment segment : segments) {
                    // 生成向量并存储
                    Embedding embedding = embeddingModel.embed(segment.text()).content();
                    knowledgeBaseEmbeddingStore.add(embedding, segment);
                    segmentCount++;
                    totalSegments++;
                }

                // 向量化成功后，记录该文件名
                saveVectorizedFile(fileName);
                log.info(">>> 文档 {} 向量化完成，共 {} 个片段", fileName, segmentCount);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info(">>> 知识库初始化完成: 新增 {} 个文本段, 跳过 {} 个已存在文档, 耗时: {}ms",
                    totalSegments, skippedFiles, duration);

        } catch (Exception e) {
            log.error(">>> 知识库初始化失败", e);
            // 不抛出异常，允许应用继续启动
        }
    }

    /**
     * 从本地文件加载已向量化的文件名列表
     */
    private Set<String> loadVectorizedFiles() {
        Set<String> vectorizedFiles = new HashSet<>();
        File file = new File(VECTORIZED_FILES_RECORD);

        if (!file.exists()) {
            log.info(">>> 向量化记录文件不存在，将创建新文件");
            return vectorizedFiles;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    vectorizedFiles.add(line);
                }
            }
        } catch (IOException e) {
            log.error(">>> 读取向量化记录文件失败", e);
        }

        return vectorizedFiles;
    }

    /**
     * 将成功向量化的文件名保存到本地文件
     */
    private void saveVectorizedFile(String fileName) {
        try {
            File file = new File(VECTORIZED_FILES_RECORD);
            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 追加文件名到记录文件
            Files.write(
                    Paths.get(VECTORIZED_FILES_RECORD),
                    (fileName + System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.error(">>> 保存向量化记录失败: {}", fileName, e);
        }
    }
}

