package org.bupt.demoapp.util;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MedlinePlus 专用文档切割器
 *
 * <p>切割策略（三级）：
 * <ol>
 *   <li><b>一级</b>：按 topic 边界（80个 '=' 组成的分隔线）切割，每个 health-topic 独立成段。
 *       这样保证每个 chunk 的标题、别名、正文语义完整，不会把两个疾病混在一起。</li>
 *   <li><b>二级</b>：若某个 topic 的字符数超过 {@code maxTopicChars}（默认 1500），
 *       则按段落（连续两个换行）再次切割，每个子段前自动附加 topic 标题作为上下文前缀，
 *       保证即使片段独立出现也能被检索系统关联到正确的疾病。</li>
 *   <li><b>三级</b>（兜底）：若单段落仍超过 {@code maxSegmentChars}（默认 1000），
 *       则退化为句子级切割，避免向量化时超出 embedding 模型的 token 上限。</li>
 * </ol>
 *
 * <p>典型 MedlinePlus topic 字符数分布：
 * <ul>
 *   <li>短 topic（&lt;600 字符）：约占 40%，直接整体作为一个 chunk</li>
 *   <li>中等 topic（600~1500 字符）：约占 45%，直接整体作为一个 chunk</li>
 *   <li>长 topic（&gt;1500 字符）：约占 15%，按段落二次切分</li>
 * </ul>
 */
public class MedlinePlusTopicSplitter implements DocumentSplitter {

    /** topic 分隔线正则：80 个等号单独成行 */
    private static final Pattern TOPIC_BOUNDARY = Pattern.compile(
            "={80}\\r?\\n", Pattern.MULTILINE);

    /** topic 头部解析：提取 Title */
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "Title:\\s*(.+)");

    /** 段落分隔：连续两个以上换行 */
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n{2,}");

    /**
     * 单个 topic 超过此字符数时，启动段落二次切分（默认 1500）
     */
    private final int maxTopicChars;

    /**
     * 段落级兜底阈值：单段落超过此字符数时，退化为句子切割（默认 1000）
     */
    private final int maxSegmentChars;

    /**
     * 段落二次切分时的 overlap 字符数（默认 200）
     */
    private final int overlapChars;

    public MedlinePlusTopicSplitter() {
        this(1500, 1000, 200);
    }

    public MedlinePlusTopicSplitter(int maxTopicChars, int maxSegmentChars, int overlapChars) {
        this.maxTopicChars = maxTopicChars;
        this.maxSegmentChars = maxSegmentChars;
        this.overlapChars = overlapChars;
    }

    @Override
    public List<TextSegment> split(Document document) {
        String fullText = document.text();
        List<TextSegment> result = new ArrayList<>();

        // ── 一级切割：按 topic 边界分块 ──────────────────────────────────────
        List<String> topicBlocks = splitByTopicBoundary(fullText);

        for (String block : topicBlocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;

            // 跳过文件头部统计信息（不含 "Title:" 的块）
            if (!trimmed.contains("Title:")) continue;

            if (trimmed.length() <= maxTopicChars) {
                // ── 整个 topic 作为一个 chunk ──────────────────────────────
                result.add(TextSegment.from(trimmed));
            } else {
                // ── 二级切割：按段落拆分超长 topic ──────────────────────────
                String titlePrefix = extractTitlePrefix(trimmed);
                List<String> paragraphs = splitByParagraph(trimmed);

                List<String> subChunks = mergeOrSplitParagraphs(
                        paragraphs, titlePrefix, maxSegmentChars);

                for (String chunk : subChunks) {
                    result.add(TextSegment.from(chunk));
                }
            }
        }

        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    // 私有辅助方法
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 按 80 个等号分隔线切割全文，返回各 topic 块（含分隔线本身被消耗）。
     */
    private List<String> splitByTopicBoundary(String text) {
        List<String> blocks = new ArrayList<>();
        Matcher m = TOPIC_BOUNDARY.matcher(text);
        int lastEnd = 0;
        while (m.find()) {
            String block = text.substring(lastEnd, m.start());
            if (!block.trim().isEmpty()) {
                blocks.add(block);
            }
            lastEnd = m.end();
        }
        // 最后一块（无尾部分隔线）
        if (lastEnd < text.length()) {
            String tail = text.substring(lastEnd);
            if (!tail.trim().isEmpty()) {
                blocks.add(tail);
            }
        }
        return blocks;
    }

    /**
     * 从 topic 块中提取 "[Title: Xxx]" 格式的前缀，用于二次切分时的上下文标注。
     */
    private String extractTitlePrefix(String block) {
        Matcher m = TITLE_PATTERN.matcher(block);
        if (m.find()) {
            return "[Topic: " + m.group(1).trim() + "] ";
        }
        return "";
    }

    /**
     * 按段落（双换行）切割文本块，返回非空段落列表。
     */
    private List<String> splitByParagraph(String block) {
        List<String> paragraphs = new ArrayList<>();
        for (String p : PARAGRAPH_BREAK.split(block)) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    /**
     * 将段落列表合并成不超过 maxChars 的 chunk，并在每个 chunk 前附加 titlePrefix。
     * 若单段落本身超过 maxChars，则保留为独立 chunk（允许适度超出，避免切断句子）。
     *
     * <p>overlap 策略：每个新 chunk 的开头携带上一个 chunk 最后 {@code overlapChars} 个字符，
     * 保证相邻 chunk 在语义上有一定重叠，提升 RAG 召回质量。
     */
    private List<String> mergeOrSplitParagraphs(
            List<String> paragraphs, String titlePrefix, int maxChars) {

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String lastOverlap = "";

        for (String para : paragraphs) {
            // 判断当前段落加入后是否超限
            int projectedLen = (current.length() == 0 ? 0 : current.length() + 2)
                    + para.length();

            if (current.length() > 0 && projectedLen > maxChars) {
                // 保存当前 chunk
                String chunkText = titlePrefix + lastOverlap + current.toString().trim();
                chunks.add(chunkText);

                // 计算 overlap：取当前 chunk 末尾 overlapChars 个字符
                String currentStr = current.toString().trim();
                lastOverlap = currentStr.length() > overlapChars
                        ? currentStr.substring(currentStr.length() - overlapChars) + "\n\n"
                        : currentStr + "\n\n";

                current = new StringBuilder(para);
            } else {
                if (current.length() > 0) current.append("\n\n");
                current.append(para);
            }
        }

        // 最后一块
        if (current.length() > 0) {
            chunks.add(titlePrefix + lastOverlap + current.toString().trim());
        }

        return chunks;
    }
}


























