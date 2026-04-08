package org.bupt.demoapp.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.bupt.demoapp.common.MemoryIds;
import org.bupt.demoapp.common.SnowflakeIdGenerator;
import org.bupt.demoapp.entity.Plan;
import org.bupt.demoapp.mapper.PlanMapper;
import org.bupt.demoapp.service.LogQueryService;
import org.bupt.demoapp.service.LogSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Planner Agent 工具集
 *
 * 每个 @Tool 是一个原子操作，由 PlannerAgent（LLM）按需调用。
 * 这是渐进式披露的核心：LLM 在需要某类数据时才调用对应 Tool，
 * 而不是在对话开始时把所有数据一次性塞入 prompt。
 *
 * 所有 Tool 均复用现有服务，不引入重复逻辑。
 *
 * 注意：直接注入 PlanMapper 而非 PlanService，避免与 PlannerAgent 形成循环依赖。
 */
@Component
public class PlannerAgentTools {

    private static final Logger logger = LoggerFactory.getLogger(PlannerAgentTools.class);

    private static final int    KNOWLEDGE_MAX_RESULTS = 5;
    private static final double KNOWLEDGE_MIN_SCORE   = 0.5;
    private static final Duration MCP_INIT_TIMEOUT    = Duration.ofSeconds(5);
    private static final Duration MCP_TOOL_TIMEOUT    = Duration.ofSeconds(30);
    private static final Duration MCP_SHUTDOWN_WAIT   = Duration.ofSeconds(2);
    private static final String MCP_SERVER_SCRIPT = "mcp_server/server.py";
    private static final java.nio.file.Path BASE_DIR = java.nio.file.Path.of(System.getProperty("user.dir"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 优先用 miniconda python（已安装 gspread），找不到则回退到 python3
    private static final String PYTHON_BIN = resolvePythonBin();

    private static String resolvePythonBin() {
        String[] candidates = {
            "/opt/miniconda3/bin/python3",
            "/usr/local/bin/python3",
            "python3"
        };
        for (String p : candidates) {
            java.io.File f = new java.io.File(p);
            if (f.exists() && f.canExecute()) {
                return p;
            }
        }
        return "python3";
    }

    // 代理配置：从 application.yaml 读取，支持环境变量覆盖
    @Value("${mcp.proxy.http:}")
    private String mcpHttpProxy;

    @Value("${mcp.proxy.https:}")
    private String mcpHttpsProxy;

    @Autowired
    private LogQueryService logQueryService;

    @Autowired
    private LogSummaryService logSummaryService;

    @Autowired
    @Qualifier("knowledgeBaseEmbeddingStore")
    private MilvusEmbeddingStore knowledgeBaseEmbeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private MemoryIds memoryIds;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    // ==================== Tool 1：获取用户健康档案 ====================

    @Tool("获取用户的健康档案摘要，包括健康状况、生活习惯、近期健康趋势")
    public String getUserHealthProfile() {
        String memoryId = PlannerContext.getMemoryId();
        logger.info(">>> [Tool调用] getUserHealthProfile - memoryId: {}", memoryId);
        long start = System.currentTimeMillis();
        try {
            String reply = logSummaryService
                    .summarize(memoryId, "请总结我最近3个月的整体健康状况")
                    .getReply();
            long duration = System.currentTimeMillis() - start;
            logger.info(">>> [Tool完成] getUserHealthProfile - 耗时: {}ms, 返回内容摘要: {}",
                    duration, reply.length() > 200 ? reply.substring(0, 200) + "..." : reply);
            return reply;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.error(">>> [Tool失败] getUserHealthProfile - 耗时: {}ms", duration, e);
            return "健康档案获取失败：" + e.getMessage();
        }
    }

    // ==================== Tool 2：检索用户历史日志 ====================

    @Tool("检索用户的历史健康日志，用于了解用户的具体行为习惯")
    public String searchUserLogs(
            @P("检索查询语句，根据当前计划需求自主构造，必须包含明确时间范围，例如：最近3个月的饮食情况、过去3个月的运动记录、近3个月的睡眠情况") String query
    ) {
        String memoryId = PlannerContext.getMemoryId();
        String finalQuery = query;
        if (!query.contains("月") && !query.contains("周") && !query.contains("天")
                && !query.contains("week") && !query.contains("month") && !query.contains("day")) {
            finalQuery = query + "（时间范围：最近3个月）";
            logger.info(">>> [Tool] searchUserLogs - query未含时间词，自动补充时间范围: {}", finalQuery);
        }
        logger.info(">>> [Tool调用] searchUserLogs - memoryId: {}, LLM构造的query: {}", memoryId, finalQuery);
        long start = System.currentTimeMillis();
        try {
            String reply = logQueryService.queryChat(memoryId, finalQuery).getReply();
            long duration = System.currentTimeMillis() - start;
            logger.info(">>> [Tool完成] searchUserLogs - 耗时: {}ms, 返回内容摘要: {}",
                    duration, reply.length() > 200 ? reply.substring(0, 200) + "..." : reply);
            return reply;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.error(">>> [Tool失败] searchUserLogs - 耗时: {}ms, query: {}", duration, finalQuery, e);
            return "日志检索失败：" + e.getMessage();
        }
    }

    // ==================== Tool 3：检索医学健康知识 ====================

    @Tool("从医学知识库检索专业健康知识，为计划提供科学依据")
    public String searchHealthKnowledge(
            @P("检索查询语句，将用户健康状况和计划类型融合成完整查询，例如：糖尿病患者饮食控糖建议") String query
    ) {
        logger.info(">>> [Tool调用] searchHealthKnowledge - LLM构造的query: {}", query);
        long start = System.currentTimeMillis();
        try {
            Embedding embedding = embeddingModel.embed(query).content();

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(KNOWLEDGE_MAX_RESULTS)
                    .minScore(KNOWLEDGE_MIN_SCORE)
                    .build();

            EmbeddingSearchResult<TextSegment> result = knowledgeBaseEmbeddingStore.search(request);

            if (result.matches().isEmpty()) {
                long duration = System.currentTimeMillis() - start;
                logger.warn(">>> [Tool完成] searchHealthKnowledge - 耗时: {}ms, 知识库无匹配结果", duration);
                return "知识库中未找到相关内容";
            }

            String knowledge = result.matches().stream()
                    .map(m -> m.embedded().text())
                    .collect(Collectors.joining("\n\n---\n\n"));

            long duration = System.currentTimeMillis() - start;
            logger.info(">>> [Tool完成] searchHealthKnowledge - 耗时: {}ms, 命中知识条数: {}, 各条得分: {}",
                    duration,
                    result.matches().size(),
                    result.matches().stream()
                            .map(m -> String.format("%.3f", m.score()))
                            .collect(Collectors.joining(", ")));
            return knowledge;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.error(">>> [Tool失败] searchHealthKnowledge - 耗时: {}ms, query: {}", duration, query, e);
            return "知识库检索失败：" + e.getMessage();
        }
    }

    // ==================== Tool 4：保存计划 ====================

    @Tool("将生成的健康计划保存到数据库并自动创建在线表格，返回planId、sheetId、sheetUrl")
    public String savePlan(
            @P("计划类型，例如：饮食计划、运动计划、睡眠计划") String planType,
            @P("计划的完整文本内容") String planContent
    ) {
        String memoryId = PlannerContext.getMemoryId();
        logger.info(">>> [Tool调用] savePlan - memoryId: {}, planType: {}", memoryId, planType);
        logger.info(">>> [Tool调用] savePlan - planContent摘要: {}",
                planContent.length() > 300 ? planContent.substring(0, 300) + "..." : planContent);

        PlannerContext.PlanStage stage = PlannerContext.getPlanStage();
        if (stage != PlannerContext.PlanStage.READY_TO_GENERATE) {
            logger.warn(">>> [Tool拦截] savePlan - 当前阶段不允许生成计划, stage: {}", stage);
            return "当前仍在信息收集阶段，请先完成个性化追问并等待用户回复后再生成计划。";
        }

        long start = System.currentTimeMillis();
        try {
            String userId = String.valueOf(memoryIds.extractUserId(memoryId));

            Plan plan = new Plan();
            plan.setUserId(userId);
            plan.setPlanType(planType);
            plan.setPlanContent(planContent);
            plan.setPlanStatus("DRAFT");
            plan.setPlanId(snowflakeIdGenerator.nextId());
            plan.setCreateTime(LocalDateTime.now());

            planMapper.insert(plan);
            Long planId = plan.getPlanId();

            Map<String, Object> args = new HashMap<>();
            args.put("planType", planType);
            args.put("planContent", planContent);
            String sheetRaw = callMcpTool("create_google_sheet", args);

            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("planId", planId);
            result.put("planType", planType);

            try {
                JsonNode sheetJson = OBJECT_MAPPER.readTree(sheetRaw);
                result.set("sheet", sheetJson);
                JsonNode sheetUrl = sheetJson.get("sheetUrl");
                if (sheetUrl != null && !sheetUrl.isNull()) {
                    result.put("sheetUrl", sheetUrl.asText());
                }
                JsonNode sheetId = sheetJson.get("sheetId");
                if (sheetId != null && !sheetId.isNull()) {
                    result.put("sheetId", sheetId.asText());
                }
            } catch (Exception parseEx) {
                result.put("sheetRaw", sheetRaw);
            }

            long duration = System.currentTimeMillis() - start;
            String saveResult = OBJECT_MAPPER.writeValueAsString(result);
            PlannerContext.setLastSavePlanResult(saveResult);
            logger.info(">>> [Tool完成] savePlan - 耗时: {}ms, planId: {}, userId: {}", duration, planId, userId);
            return saveResult;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.error(">>> [Tool失败] savePlan - 耗时: {}ms, planType: {}", duration, planType, e);
            return "计划保存失败：" + e.getMessage();
        }
    }

    // ==================== Tool 5：更新计划 ====================

    @Tool("更新已保存的计划内容或状态，用户确认满意后将状态改为 ACTIVE")
    public String updatePlan(
            @P("计划ID，由 savePlan 返回") Long planId,
            @P("更新后的���划完整内容") String newContent,
            @P("计划状态：DRAFT（修改中）/ ACTIVE（用户已确认）") String newStatus
    ) {
        logger.info(">>> [Tool调用] updatePlan - planId: {}, newStatus: {}", planId, newStatus);
        logger.info(">>> [Tool调用] updatePlan - newContent摘要: {}",
                newContent.length() > 300 ? newContent.substring(0, 300) + "..." : newContent);
        long start = System.currentTimeMillis();
        try {
            Plan plan = planMapper.findById(planId);
            if (plan == null) {
                logger.warn(">>> [Tool警告] updatePlan - planId: {} 不存在", planId);
                return "计划不存在，planId: " + planId;
            }
            String oldStatus = plan.getPlanStatus();
            plan.setPlanContent(newContent);
            plan.setPlanStatus(newStatus);
            plan.setUpdateTime(LocalDateTime.now());
            planMapper.update(plan);

            if ("ACTIVE".equals(newStatus)) {
                PlannerContext.markPlanEnded();
                logger.info(">>> [Tool] updatePlan - 计划已激活，标记 PLAN 流程结束 - planId: {}", planId);
            }

            long duration = System.currentTimeMillis() - start;
            logger.info(">>> [Tool完成] updatePlan - 耗时: {}ms, planId: {}, 状态流转: {} -> {}",
                    duration, planId, oldStatus, newStatus);
            return "计划已更新，planId: " + planId + "，当前状态: " + newStatus;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.error(">>> [Tool失败] updatePlan - 耗时: {}ms, planId: {}", duration, planId, e);
            return "计划更新失败：" + e.getMessage();
        }
    }

    // ==================== Tool 6：创建 Google Sheets 在线表格 ====================

    @Tool("根据基础计划创建 Google Sheets 在线表格并返回可编辑链接")
    public String createGoogleSheet(
            @P("计划类型，例如：饮食计划、运动计划、睡眠计划") String planType,
            @P("计划的完整文本内容") String planContent
    ) {
        logger.info(">>> [Tool调用] createGoogleSheet - planType: {}", planType);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("planType", planType);
            args.put("planContent", planContent);
            String output = callMcpTool("create_google_sheet", args);
            long duration = System.currentTimeMillis() - start;
            logger.info(">>> [Tool完成] createGoogleSheet - 耗时: {}ms, 输出: {}", duration, output);
            return output;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.error(">>> [Tool失败] createGoogleSheet - 耗时: {}ms", duration, e);
            return "在线表格创建失败：" + e.getMessage();
        }
    }

    // ==================== Tool 7：读取 Google Sheets 在线表格 ====================

    @Tool("读取用户已编辑的 Google Sheets 表格内容")
    public String readGoogleSheet(
            @P("Google Sheets 的 sheetId") String sheetId
    ) {
        logger.info(">>> [Tool调用] readGoogleSheet - sheetId: {}", sheetId);
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("sheetId", sheetId);
            String output = callMcpTool("read_google_sheet", args);
            long duration = System.currentTimeMillis() - start;
            logger.info(">>> [Tool完成] readGoogleSheet - 耗时: {}ms", duration);
            return output;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            logger.error(">>> [Tool失败] readGoogleSheet - 耗时: {}ms", duration, e);
            return "读取在线表格失败：" + e.getMessage();
        }
    }

    private String callMcpTool(String toolName, Map<String, Object> arguments) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(PYTHON_BIN, MCP_SERVER_SCRIPT, "--mcp");
        pb.directory(BASE_DIR.toFile());

        Map<String, String> env = pb.environment();
        String httpProxy  = System.getenv("http_proxy")  != null ? System.getenv("http_proxy")  : System.getenv("HTTP_PROXY");
        String httpsProxy = System.getenv("https_proxy") != null ? System.getenv("https_proxy") : System.getenv("HTTPS_PROXY");
        String allProxy   = System.getenv("all_proxy")   != null ? System.getenv("all_proxy")   : System.getenv("ALL_PROXY");
        String jvmProxyHost = System.getProperty("https.proxyHost");
        String jvmProxyPort = System.getProperty("https.proxyPort");
        if (jvmProxyHost != null && jvmProxyPort != null && httpsProxy == null) {
            httpsProxy = "http://" + jvmProxyHost + ":" + jvmProxyPort;
            httpProxy  = httpsProxy;
        }
        if (mcpHttpProxy  != null && !mcpHttpProxy.isEmpty())  { httpProxy  = mcpHttpProxy;  }
        if (mcpHttpsProxy != null && !mcpHttpsProxy.isEmpty()) { httpsProxy = mcpHttpsProxy; }

        if (httpProxy  != null) { env.put("http_proxy",  httpProxy);  env.put("HTTP_PROXY",  httpProxy);  }
        if (httpsProxy != null) { env.put("https_proxy", httpsProxy); env.put("HTTPS_PROXY", httpsProxy); }
        if (allProxy   != null) { env.put("all_proxy",   allProxy);   env.put("ALL_PROXY",   allProxy);   }
        logger.info(">>> [MCP] 启动 Python: {}, http_proxy={}, https_proxy={}", PYTHON_BIN, httpProxy, httpsProxy);

        Process process = pb.start();
        final StringBuilder stderrCapture = new StringBuilder();
        Thread stderrThread = new Thread(() -> {
            try (InputStream es = process.getErrorStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = es.read(buf)) != -1) {
                    stderrCapture.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
        });
        stderrThread.setDaemon(true);
        stderrThread.start();
        try (OutputStream outputStream = process.getOutputStream();
             InputStream inputStream = process.getInputStream()) {

            ObjectNode initParams = OBJECT_MAPPER.createObjectNode();
            initParams.put("protocolVersion", "2024-11-05");
            initParams.set("capabilities", OBJECT_MAPPER.createObjectNode());
            ObjectNode clientInfo = OBJECT_MAPPER.createObjectNode();
            clientInfo.put("name", "demoapp");
            clientInfo.put("version", "0.0.1");
            initParams.set("clientInfo", clientInfo);
            writeMcpMessage(outputStream, buildRequest(1, "initialize", initParams));

            JsonNode initResponse = readMcpMessage(inputStream, process, MCP_INIT_TIMEOUT, stderrCapture);
            if (initResponse == null || initResponse.get("error") != null) {
                throw new RuntimeException("MCP initialize 失败: " + initResponse);
            }

            writeMcpMessage(outputStream, buildNotification("notifications/initialized", OBJECT_MAPPER.createObjectNode()));

            ObjectNode callParams = OBJECT_MAPPER.createObjectNode();
            callParams.put("name", toolName);
            callParams.set("arguments", OBJECT_MAPPER.valueToTree(arguments));
            writeMcpMessage(outputStream, buildRequest(2, "tools/call", callParams));

            JsonNode response;
            do {
                response = readMcpMessage(inputStream, process, MCP_TOOL_TIMEOUT, stderrCapture);
            } while (response != null && (!response.has("id") || response.get("id").asInt() != 2));

            if (response == null) {
                throw new RuntimeException("MCP tools/call 无响应。stderr: " + stderrCapture);
            }

            if (response.get("error") != null) {
                throw new RuntimeException("MCP tools/call 失败: " + response.get("error"));
            }

            JsonNode result = response.get("result");
            if (result == null) {
                throw new RuntimeException("MCP tools/call 响应缺少 result");
            }

            JsonNode content = result.get("content");
            if (content != null && content.isArray() && !content.isEmpty()) {
                JsonNode first = content.get(0);
                JsonNode textNode = first.get("text");
                if (textNode != null) {
                    return textNode.asText();
                }
            }

            return result.toString();
        } finally {
            process.destroy();
            stderrThread.join(MCP_SHUTDOWN_WAIT.toMillis());
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            if (stderrCapture.length() > 0) {
                logger.debug(">>> [MCP stderr] {}", stderrCapture);
            }
            process.waitFor();
        }
    }

    private String buildRequest(int id, String method, JsonNode params) throws Exception {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        node.put("method", method);
        node.set("params", params);
        return OBJECT_MAPPER.writeValueAsString(node);
    }

    private String buildNotification(String method, JsonNode params) throws Exception {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("method", method);
        node.set("params", params);
        return OBJECT_MAPPER.writeValueAsString(node);
    }

    private void writeMcpMessage(OutputStream outputStream, String json) throws Exception {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + body.length + "\r\n\r\n";
        outputStream.write(header.getBytes(StandardCharsets.UTF_8));
        outputStream.write(body);
        outputStream.flush();
    }

    private JsonNode readMcpMessage(InputStream inputStream, Process process,
                                     Duration timeout, StringBuilder stderrCapture) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive() && inputStream.available() == 0) {
                throw new RuntimeException("MCP 进程已退出。stderr: " + stderrCapture);
            }
            if (inputStream.available() == 0) {
                Thread.sleep(50);
                continue;
            }

            String header = readHeader(inputStream);
            if (header == null || header.isEmpty()) {
                return null;
            }

            int contentLength = -1;
            for (String line : header.split("\\r\\n")) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
                    break;
                }
            }

            if (contentLength <= 0) {
                return null;
            }

            byte[] body = inputStream.readNBytes(contentLength);
            if (body.length != contentLength) {
                return null;
            }

            return OBJECT_MAPPER.readTree(body);
        }

        throw new RuntimeException("MCP 响应超时，timeout=" + timeout.toSeconds() + "s, stderr: " + stderrCapture);
    }

    private String readHeader(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int matched = 0;
        byte[] end = new byte[]{'\r', '\n', '\r', '\n'};

        while (true) {
            int available = inputStream.available();
            if (available <= 0) {
                return null;
            }

            int b = inputStream.read();
            if (b == -1) {
                return null;
            }

            buffer.write(b);
            if (b == end[matched]) {
                matched++;
                if (matched == end.length) {
                    byte[] bytes = buffer.toByteArray();
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            } else {
                matched = (b == end[0]) ? 1 : 0;
            }
        }
    }
}
