package com.timxs.interactionplus.core.support;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * SVG 安全校验。检测到危险内容直接判定为不安全，不做自动修复。
 *
 * <p>最低校验：禁止 {@code <script>}、事件属性（on*）、{@code javascript:} URL、
 * 外链脚本、{@code <foreignObject>}。
 */
public final class SvgSanitizer {

    private SvgSanitizer() {
    }

    /**
     * 校验 SVG 文本是否安全。
     *
     * @param svgContent SVG 文本内容
     * @return 安全返回 true；解析失败或含危险内容返回 false
     */
    public static boolean isSafe(String svgContent) {
        if (svgContent == null || svgContent.isBlank()) {
            return false;
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // 防御 XXE：禁用 DTD 与外部实体
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);

            var builder = factory.newDocumentBuilder();
            var document = builder.parse(
                new ByteArrayInputStream(svgContent.getBytes(StandardCharsets.UTF_8)));
            return isElementSafe(document.getDocumentElement());
        } catch (Exception e) {
            // 解析失败（含被 DTD 拦截）一律视为不安全
            return false;
        }
    }

    private static boolean isElementSafe(Element element) {
        if (element == null) {
            return true;
        }
        String localName = localName(element);
        // 禁止 script 与 foreignObject
        if ("script".equalsIgnoreCase(localName) || "foreignobject".equalsIgnoreCase(localName)) {
            return false;
        }
        if (!areAttributesSafe(element)) {
            return false;
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                && !isElementSafe((Element) child)) {
                return false;
            }
        }
        return true;
    }

    private static boolean areAttributesSafe(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return true;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String name = attr.getNodeName();
            String value = attr.getNodeValue();
            // 事件属性：on*
            if (name != null && name.toLowerCase().startsWith("on")) {
                return false;
            }
            // javascript: URL（含 href / xlink:href / src 等任意属性）
            if (value != null && containsJavascriptUrl(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检测 javascript: URL，含 URL 编码变体（如 {@code javascript%3a}）。
     * HTML 实体变体（如 {@code javascript&colon;}）非 XML 预定义实体，
     * 解析阶段即失败并被 {@link #isSafe} 判定为不安全，无需在此处理。
     */
    private static boolean containsJavascriptUrl(String value) {
        String normalized = value.replaceAll("\\s", "").toLowerCase();
        if (normalized.contains("javascript:")) {
            return true;
        }
        // 纵深防御：URL 解码后再检一次编码变体
        try {
            String decoded = java.net.URLDecoder.decode(normalized, StandardCharsets.UTF_8);
            return decoded.replaceAll("\\s", "").contains("javascript:");
        } catch (IllegalArgumentException e) {
            // 非法百分号序列：解码失败不据此放行，按原始串判定结果返回
            return false;
        }
    }

    private static String localName(Element element) {
        String localName = element.getLocalName();
        if (localName != null) {
            return localName;
        }
        // 无命名空间时回退到 tagName，并去除可能的前缀
        String tagName = element.getTagName();
        int idx = tagName.indexOf(':');
        return idx >= 0 ? tagName.substring(idx + 1) : tagName;
    }
}
