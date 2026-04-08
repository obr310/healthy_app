package org.bupt.demoapp.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MedlinePlus XML 健康主题解析器
 * 
 * 功能：
 * 1. 解析 MedlinePlus XML 文件
 * 2. 提取每个 health-topic 的 full-summary
 * 3. 清洗 HTML 标签，转为纯文本
 * 4. 导出为文本文件或结构化数据
 */
public class MedlinePlusXmlParser {

    /**
     * 健康主题数据对象
     */
    public static class HealthTopic {
        private String id;
        private String title;
        private String url;
        private String summary;
        private List<String> alsoCalled;
        
        public HealthTopic() {
            this.alsoCalled = new ArrayList<>();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        
        public List<String> getAlsoCalled() { return alsoCalled; }
        public void addAlsoCalled(String name) { this.alsoCalled.add(name); }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=".repeat(80)).append("\n");
            sb.append("Title: ").append(title).append("\n");
            sb.append("ID: ").append(id).append("\n");
            if (!alsoCalled.isEmpty()) {
                sb.append("Also called: ").append(String.join(", ", alsoCalled)).append("\n");
            }
            sb.append("-".repeat(80)).append("\n");
            sb.append(summary).append("\n");
            sb.append("=".repeat(80)).append("\n\n");
            return sb.toString();
        }
    }

    /**
     * 解析 XML 文件
     * 
     * @param xmlFilePath XML 文件路径
     * @return 健康主题列表
     */
    public List<HealthTopic> parseXmlFile(String xmlFilePath) throws Exception {
        return parseXmlFile(xmlFilePath, "English");
    }

    /**
     * 解析 XML 文件（带语言过滤）
     * 
     * @param xmlFilePath XML 文件路径
     * @param languageFilter 语言过滤器 ("English", "Spanish", "all")
     * @return 健康主题列表
     */
    public List<HealthTopic> parseXmlFile(String xmlFilePath, String languageFilter) throws Exception {
        List<HealthTopic> topics = new ArrayList<>();
        
        File xmlFile = new File(xmlFilePath);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();
        
        NodeList healthTopics = doc.getElementsByTagName("health-topic");
        System.out.println("找到 " + healthTopics.getLength() + " 个健康主题");
        
        int filteredCount = 0;
        for (int i = 0; i < healthTopics.getLength(); i++) {
            Node node = healthTopics.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                
                // 语言过滤
                String language = element.getAttribute("language");
                if (language.isEmpty()) {
                    language = "English"; // 默认英语
                }
                
                if (!"all".equals(languageFilter) && !language.equals(languageFilter)) {
                    filteredCount++;
                    continue;
                }
                
                HealthTopic topic = parseHealthTopic(element);
                if (topic != null && topic.getSummary() != null && !topic.getSummary().trim().isEmpty()) {
                    topics.add(topic);
                }
            }
        }
        
        if (filteredCount > 0) {
            System.out.println("✓ 过滤掉 " + filteredCount + " 个非" + languageFilter + "主题");
        }
        System.out.println("✓ 有效主题: " + topics.size());
        
        return topics;
    }

    /**
     * 解析单个 health-topic 元素
     */
    private HealthTopic parseHealthTopic(Element element) {
        HealthTopic topic = new HealthTopic();
        
        // 提取属性
        topic.setId(element.getAttribute("id"));
        topic.setTitle(element.getAttribute("title"));
        topic.setUrl(element.getAttribute("url"));
        
        // 提取别名
        NodeList alsoCalledNodes = element.getElementsByTagName("also-called");
        for (int i = 0; i < alsoCalledNodes.getLength(); i++) {
            String alsoCalled = alsoCalledNodes.item(i).getTextContent().trim();
            if (!alsoCalled.isEmpty()) {
                topic.addAlsoCalled(alsoCalled);
            }
        }
        
        // 提取 full-summary 并清洗
        NodeList summaryNodes = element.getElementsByTagName("full-summary");
        if (summaryNodes.getLength() > 0) {
            String rawSummary = summaryNodes.item(0).getTextContent();
            String cleanedSummary = cleanHtmlContent(rawSummary);
            topic.setSummary(cleanedSummary);
        }
        
        return topic;
    }

    /**
     * 清洗 HTML 内容
     * 
     * 1. 替换 HTML 实体（&lt; &gt; &amp; 等）
     * 2. 移除 HTML 标签
     * 3. 保留文本内容和基本格式
     */
    private String cleanHtmlContent(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        
        // 1. 解码 HTML 实体
        String decoded = html
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ");
        
        // 2. 处理特殊标签（保留换行）
        decoded = decoded
            .replaceAll("<br\\s*/?>", "\n")
            .replaceAll("</p>", "\n\n")
            .replaceAll("<li>", "\n• ")
            .replaceAll("</li>", "")
            .replaceAll("<ul>", "\n")
            .replaceAll("</ul>", "\n");
        
        // 3. 移除所有剩余的 HTML 标签
        decoded = decoded.replaceAll("<[^>]+>", "");
        
        // 4. 清理多余空白
        decoded = decoded
            .replaceAll("[ \\t]+", " ")           // 多个空格/制表符 -> 单个空格
            .replaceAll("\\n{3,}", "\n\n")        // 多个换行 -> 最多2个
            .trim();
        
        return decoded;
    }

    /**
     * 导出为单个纯文本文件
     */
    public void exportToSingleFile(List<HealthTopic> topics, String outputPath) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("MedlinePlus Health Topics Knowledge Base\n");
            writer.write("=" .repeat(80) + "\n");
            writer.write("Total: " + topics.size() + " topics\n");
            writer.write("=" .repeat(80) + "\n\n");
            
            for (HealthTopic topic : topics) {
                writer.write(topic.toString());
            }
            
            System.out.println("✓ 已导出到: " + outputPath);
        }
    }

    /**
     * 导出为多个文件（每个主题一个文件）
     */
    public void exportToMultipleFiles(List<HealthTopic> topics, String outputDir) throws IOException {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        for (int i = 0; i < topics.size(); i++) {
            HealthTopic topic = topics.get(i);
            String fileName = String.format("%s/%s_%s.txt", 
                outputDir, 
                topic.getId(), 
                sanitizeFilename(topic.getTitle()));
            
            try (FileWriter writer = new FileWriter(fileName, StandardCharsets.UTF_8)) {
                writer.write(topic.toString());
            }
            
            if ((i + 1) % 100 == 0) {
                System.out.println("已处理: " + (i + 1) + "/" + topics.size());
            }
        }
        
        System.out.println("✓ 已导出 " + topics.size() + " 个文件到: " + outputDir);
    }

    /**
     * 清理文件名中的非法字符
     */
    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_")
                   .substring(0, Math.min(name.length(), 50));
    }

    /**
     * 统计信息
     */
    public void printStatistics(List<HealthTopic> topics) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("统计信息");
        System.out.println("=".repeat(60));
        System.out.println("总主题数: " + topics.size());
        
        int totalChars = topics.stream()
            .mapToInt(t -> t.getSummary() != null ? t.getSummary().length() : 0)
            .sum();
        System.out.println("总字符数: " + totalChars);
        System.out.println("平均字符数/主题: " + (totalChars / topics.size()));
        
        long withAliases = topics.stream()
            .filter(t -> !t.getAlsoCalled().isEmpty())
            .count();
        System.out.println("有别名的主题: " + withAliases);
        System.out.println("=".repeat(60) + "\n");
    }

    /**
     * 主函数示例
     */
    public static void main(String[] args) {
        try {
            MedlinePlusXmlParser parser = new MedlinePlusXmlParser();
            
            // 1. 解析 XML
            String xmlPath = "/Users/fangjing/Downloads/medlineplus_health_topics.xml";
            System.out.println("正在解析: " + xmlPath);
            List<HealthTopic> topics = parser.parseXmlFile(xmlPath);
            
            // 2. 打印统计
            parser.printStatistics(topics);
            
            // 3. 导出为单个文件
            String singleFilePath = "/Users/fangjing/Downloads/health_topics_cleaned.txt";
            parser.exportToSingleFile(topics, singleFilePath);
            

            
            System.out.println("\n✓ 处理完成！");
            
        } catch (Exception e) {
            System.err.println("✗ 处理失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
