package org.bupt.demoapp.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import org.bupt.demoapp.util.MedlinePlusTopicSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * 知识库初始化器
 *
 * 应用启动时自动加载 content/ 目录下的医学知识库文档，
 * 按段落切分后：
 * 1. 存入 Milvus knowledge_base_vectors collection（向量检索）
 * 2. 存入 Elasticsearch 索引（BM25 关键词检索）
 */
@Component
public class KnowledgeBaseInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);

    private static final String VECTORIZED_FILES_RECORD = "data/vectorized_files.txt";
    private static final String ES_INDEXED_FILES_RECORD = "data/es_indexed_files.txt";

    /** ES 索引名，可在 application.yaml 中覆盖 */
    @Value("${elasticsearch.knowledge-index:knowledge_base_bm25}")
    private String esIndexName;

    /** 用户日志 ES 索引名 */
    @Value("${elasticsearch.user-log-index:user_log_bm25}")
    private String esUserLogIndexName;

    @Autowired
    @Qualifier("knowledgeBaseEmbeddingStore")
    private MilvusEmbeddingStore knowledgeBaseEmbeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private ElasticsearchClient esClient;

    @Override
    public void run(String... args) throws Exception {
        log.info(">>> 开始初始化知识库...");

        // 确保用户日志 ES 索引存在
        ensureEsUserLogIndex();

        try {
            List<Document> documents = ClassPathDocumentLoader.loadDocuments("content");
            log.info(">>> 加载知识库文档: {} 个文件", documents.size());

            if (documents.isEmpty()) {
                log.warn(">>> 未找到知识库文档，跳过初始化");
                return;
            }

            Set<String> vectorizedFiles = loadVectorizedFiles();
            log.info(">>> 已向量化的文档(Milvus): {}", vectorizedFiles);

            Set<String> esIndexedFiles = loadEsIndexedFiles();
            log.info(">>> 已索引的文档(ES): {}", esIndexedFiles);

            // 确保 ES 索引存在
            ensureEsIndex();

            DocumentSplitter splitter = new MedlinePlusTopicSplitter();

            int totalSegments = 0;
            int skippedFiles = 0;
            long startTime = System.currentTimeMillis();

            for (Document doc : documents) {
                String fileName = doc.metadata().getString("file_name");
                List<TextSegment> segments = splitter.split(doc);

                boolean alreadyInMilvus = vectorizedFiles.contains(fileName);
                boolean alreadyInEs = esIndexedFiles.contains(fileName);

                if (alreadyInMilvus && alreadyInEs) {
                    log.info(">>> 跳过已处理的文档（Milvus + ES 均已存在）: {}", fileName);
                    skippedFiles++;
                    continue;
                }

                log.info(">>> 处理文档: {}, 切分为 {} 个片段, alreadyInMilvus={}, alreadyInEs={}",
                        fileName, segments.size(), alreadyInMilvus, alreadyInEs);

                // 仅当 Milvus 中不存在时才写入 Milvus
                if (!alreadyInMilvus) {
                    for (TextSegment segment : segments) {
                        Embedding embedding = embeddingModel.embed(segment.text()).content();
                        knowledgeBaseEmbeddingStore.add(embedding, segment);
                        totalSegments++;
                    }
                    saveVectorizedFile(fileName);
                    log.info(">>> 文档 {} 写入 Milvus 完成，共 {} 个片段", fileName, segments.size());
                }

                // 仅当 ES 中不存在时才写入 ES
                if (!alreadyInEs) {
                    indexSegmentsToEs(fileName, segments);
                    saveEsIndexedFile(fileName);
                    log.info(">>> 文档 {} 写入 ES 完成", fileName);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info(">>> 知识库初始化完成: 新增 {} 个文本段, 跳过 {} 个已存在文档, 耗时: {}ms",
                    totalSegments, skippedFiles, duration);

        } catch (Exception e) {
            log.error(">>> 知识库初始化失败", e);
        }
    }

    /**
     * BM25 检索：在 Elasticsearch 中对关键词查询进行检索
     *
     * @param queryKeywords 关键词查询字符串（q_kw）
     * @param maxResults    最多返回结果数
     * @return 按 BM25 分数排序的文本列表（索引 0 = 最相关）
     */
    public List<String> bm25Search(String queryKeywords, int maxResults) {
        List<String> results = new ArrayList<>();
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                            .index(esIndexName)
                            .size(maxResults)
                            .query(q -> q
                                    .match(m -> m
                                            .field("text")
                                            .query(queryKeywords)
                                    )
                            ),
                    Map.class
            );

            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    Object text = hit.source().get("text");
                    if (text != null) {
                        results.add(text.toString());
                    }
                }
            }
            log.info(">>> BM25 检索完成 - 关键词: [{}], 召回: {} 条", queryKeywords, results.size());
        } catch (Exception e) {
            log.error(">>> BM25 检索失败 - queryKeywords: {}", queryKeywords, e);
        }
        return results;
    }

    /**
     * BM25 检索用户日志：在 Elasticsearch user_log_bm25 索引中检索
     *
     * @param queryKeywords 关键词查询字符串
     * @param userId        用户 ID（用于过滤）
     * @param maxResults    最多返回结果数
     * @return 按 BM25 分数排序的文本列表（索引 0 = 最相关）
     */
    public List<String> bm25SearchUserLog(String queryKeywords, String userId, int maxResults) {
        List<String> results = new ArrayList<>();
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                            .index(esUserLogIndexName)
                            .size(maxResults)
                            .query(q -> q
                                    .bool(b -> b
                                            .must(m -> m
                                                    .match(mt -> mt
                                                            .field("text")
                                                            .query(queryKeywords)
                                                    )
                                            )
                                            .filter(f -> f
                                                    .term(t -> t
                                                            .field("user_id")
                                                            .value(userId)
                                                    )
                                            )
                                    )
                            ),
                    Map.class
            );

            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    Object text = hit.source().get("text");
                    if (text != null) {
                        results.add(text.toString());
                    }
                }
            }
            log.info(">>> 用户日志 BM25 检索完成 - userId: {}, 关键词: [{}], 召回: {} 条", userId, queryKeywords, results.size());
        } catch (Exception e) {
            log.error(">>> 用户日志 BM25 检索失败 - userId: {}, queryKeywords: {}", userId, queryKeywords, e);
        }
        return results;
    }

    /**
     * 将单条用户日志写入 ES user_log_bm25 索引
     *
     * @param logId  日志唯一 ID
     * @param userId 用户 ID
     * @param text   日志文本内容
     */
    public void indexUserLog(String logId, String userId, String text) {
        try {
            Map<String, String> source = new HashMap<>();
            source.put("text", text);
            source.put("user_id", userId);
            source.put("log_id", logId);
            esClient.index(i -> i
                    .index(esUserLogIndexName)
                    .id(logId)
                    .document(source)
            );
            log.info(">>> 用户日志写入 ES 成功 - logId: {}, userId: {}", logId, userId);
        } catch (Exception e) {
            log.error(">>> 用户日志写入 ES 失败 - logId: {}, userId: {}", logId, userId, e);
        }
    }

    /**
     * 确保用户日志 ES 索引存在，不存在则创建
     */
    private void ensureEsUserLogIndex() {
        try {
            BooleanResponse exists = esClient.indices().exists(
                    ExistsRequest.of(e -> e.index(esUserLogIndexName))
            );
            if (!exists.value()) {
                esClient.indices().create(
                        CreateIndexRequest.of(c -> c
                                .index(esUserLogIndexName)
                                .mappings(m -> m
                                        .properties("text", p -> p
                                                .text(t -> t.analyzer("standard"))
                                        )
                                        .properties("user_id", p -> p
                                                .keyword(k -> k)
                                        )
                                        .properties("log_id", p -> p
                                                .keyword(k -> k)
                                        )
                                )
                        )
                );
                log.info(">>> 用户日志 ES 索引 [{}] 创建成功", esUserLogIndexName);
            } else {
                log.info(">>> 用户日志 ES 索引 [{}] 已存在", esUserLogIndexName);
            }
        } catch (Exception e) {
            log.error(">>> 确保用户日志 ES 索引存在时出错", e);
        }
    }

    /**
     * 确保 ES 索引存在，不存在则创建
     */
    private void ensureEsIndex() {
        try {
            BooleanResponse exists = esClient.indices().exists(
                    ExistsRequest.of(e -> e.index(esIndexName))
            );
            if (!exists.value()) {
                esClient.indices().create(
                        CreateIndexRequest.of(c -> c
                                .index(esIndexName)
                                .mappings(m -> m
                                        .properties("text", p -> p
                                                .text(t -> t.analyzer("standard"))
                                        )
                                )
                        )
                );
                log.info(">>> ES 索引 [{}] 创建成功", esIndexName);
            } else {
                log.info(">>> ES 索引 [{}] 已存在", esIndexName);
            }
        } catch (Exception e) {
            log.error(">>> 确保 ES 索引存在时出错", e);
        }
    }

    /**
     * 批量将文本段写入 ES
     */
    private void indexSegmentsToEs(String fileName, List<TextSegment> segments) {
        try {
            List<BulkOperation> ops = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                final String text = segments.get(i).text();
                final String docId = fileName + "_" + i;
                Map<String, String> source = new HashMap<>();
                source.put("text", text);
                source.put("file_name", fileName);
                ops.add(BulkOperation.of(b -> b
                        .index(idx -> idx
                                .index(esIndexName)
                                .id(docId)
                                .document(source)
                        )
                ));
            }

            if (!ops.isEmpty()) {
                esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
                log.info(">>> ES 批量写入完成 - 文件: {}, {} 条", fileName, ops.size());
            }
        } catch (Exception e) {
            log.error(">>> ES 批量写入失败 - 文件: {}", fileName, e);
        }
    }

    private Set<String> loadEsIndexedFiles() {
        Set<String> esIndexedFiles = new HashSet<>();
        File file = new File(ES_INDEXED_FILES_RECORD);
        if (!file.exists()) {
            log.info(">>> ES 索引记录文件不存在，将创建新文件");
            return esIndexedFiles;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    esIndexedFiles.add(line);
                }
            }
        } catch (IOException e) {
            log.error(">>> 读取 ES 索引记录文件失败", e);
        }
        return esIndexedFiles;
    }

    private void saveEsIndexedFile(String fileName) {
        try {
            File file = new File(ES_INDEXED_FILES_RECORD);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Files.write(
                    Paths.get(ES_INDEXED_FILES_RECORD),
                    (fileName + System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.error(">>> 保存 ES 索引记录失败: {}", fileName, e);
        }
    }

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

    private void saveVectorizedFile(String fileName) {
        try {
            File file = new File(VECTORIZED_FILES_RECORD);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
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
