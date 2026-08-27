package com.codehub.ai;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码切分器 — 按类/方法/函数粒度切分Java代码
 * 提取imports、类名、方法名用于索引
 */
@Component
public class CodeChunker {

    // 匹配 import 语句
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+[\\w.]+\\s*;", Pattern.MULTILINE);

    // 匹配类/接口声明
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?:public\\s+)?(?:abstract\\s+)?(?:class|interface|enum)\\s+(\\w+)",
            Pattern.MULTILINE);

    // 匹配方法声明（简化版，覆盖大部分场景）
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "(?:public|private|protected)?\\s*(?:static\\s+)?(?:\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\([^)]*\\)",
            Pattern.MULTILINE);

    @Data
    public static class CodeChunk {
        private String chunkType;    // CLASS / METHOD / IMPORT
        private String chunkName;    // 类名或方法名
        private String content;      // 代码内容
        private String imports;      // import列表（逗号分隔）
        private String filePath;     // 文件路径
    }

    /**
     * 切分代码文件
     */
    public List<CodeChunk> chunkCode(String filePath, String code) {
        List<CodeChunk> chunks = new ArrayList<>();

        // 1. 提取所有import
        List<String> imports = extractImports(code);
        String importStr = String.join(", ", imports);

        // 2. 提取类
        Matcher classMatcher = CLASS_PATTERN.matcher(code);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            CodeChunk chunk = new CodeChunk();
            chunk.setChunkType("CLASS");
            chunk.setChunkName(className);
            chunk.setContent(truncate(code, 2000));  // 限制长度
            chunk.setImports(importStr);
            chunk.setFilePath(filePath);
            chunks.add(chunk);
        }

        // 3. 提取方法
        Matcher methodMatcher = METHOD_PATTERN.matcher(code);
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            // 跳过常见关键字
            if (isKeyword(methodName)) continue;

            CodeChunk chunk = new CodeChunk();
            chunk.setChunkType("METHOD");
            chunk.setChunkName(methodName);
            chunk.setContent(extractMethodBody(code, methodMatcher.start()));
            chunk.setImports(importStr);
            chunk.setFilePath(filePath);
            chunks.add(chunk);
        }

        // 如果没提取到任何内容，整文件作为一个chunk
        if (chunks.isEmpty()) {
            CodeChunk chunk = new CodeChunk();
            chunk.setChunkType("FILE");
            chunk.setChunkName(filePath);
            chunk.setContent(truncate(code, 2000));
            chunk.setImports(importStr);
            chunk.setFilePath(filePath);
            chunks.add(chunk);
        }

        return chunks;
    }

    private List<String> extractImports(String code) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(code);
        while (matcher.find()) {
            imports.add(matcher.group().replace("import ", "").replace(";", "").trim());
        }
        return imports;
    }

    /**
     * 提取方法体（从方法声明到下一个方法声明或类结束）
     */
    private String extractMethodBody(String code, int start) {
        int braceCount = 0;
        int i = start;
        boolean foundOpen = false;

        while (i < code.length()) {
            char c = code.charAt(i);
            if (c == '{') {
                braceCount++;
                foundOpen = true;
            } else if (c == '}') {
                braceCount--;
                if (foundOpen && braceCount == 0) {
                    return truncate(code.substring(start, i + 1), 500);
                }
            }
            i++;
        }
        return truncate(code.substring(start), 500);
    }

    private boolean isKeyword(String name) {
        return switch (name) {
            case "if", "for", "while", "switch", "try", "catch", "return", "new", "throw" -> true;
            default -> false;
        };
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
