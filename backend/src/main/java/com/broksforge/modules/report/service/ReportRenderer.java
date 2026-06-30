package com.broksforge.modules.report.service;

import com.broksforge.common.exception.ApiException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.util.Csv;
import com.broksforge.modules.report.domain.ReportFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders {@link ReportData} into a chosen {@link ReportFormat}. A single abstraction
 * over all formats means adding PDF later is one new branch behind the same call site
 * (see ADR 0009). HTML and CSV output is encoded to neutralise XSS and CSV/formula
 * injection — important because report content originates from user-supplied data.
 */
@Component
public class ReportRenderer {

    private final ObjectMapper objectMapper;

    public ReportRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RenderedReport render(ReportFormat format, String baseName, ReportData data) {
        String safeName = sanitizeFilename(baseName);
        return switch (format) {
            case JSON -> new RenderedReport(safeName + ".json", "application/json", renderJson(data));
            case CSV -> new RenderedReport(safeName + ".csv", "text/csv", renderCsv(data));
            case HTML -> new RenderedReport(safeName + ".html", "text/html; charset=utf-8", renderHtml(data));
        };
    }

    private String renderJson(ReportData data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data.jsonModel());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.REPORT_GENERATION_FAILED, "Failed to render JSON report");
        }
    }

    private String renderCsv(ReportData data) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(data.headers());
        rows.addAll(data.rows());
        return Csv.write(rows);
    }

    private String renderHtml(ReportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        sb.append("<title>").append(htmlEscape(data.title())).append("</title>");
        sb.append("<style>")
                .append("body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;margin:2rem;color:#111}")
                .append("h1{font-size:1.25rem}")
                .append("table{border-collapse:collapse;width:100%;font-size:.85rem}")
                .append("th,td{border:1px solid #e5e7eb;padding:.4rem .6rem;text-align:left;vertical-align:top}")
                .append("th{background:#f9fafb}")
                .append("tr:nth-child(even){background:#fcfcfd}")
                .append("</style></head><body>");
        sb.append("<h1>").append(htmlEscape(data.title())).append("</h1>");
        sb.append("<table><thead><tr>");
        for (String header : data.headers()) {
            sb.append("<th>").append(htmlEscape(header)).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (List<String> row : data.rows()) {
            sb.append("<tr>");
            for (String cell : row) {
                sb.append("<td>").append(htmlEscape(cell)).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table></body></html>");
        return sb.toString();
    }

    private String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String sanitizeFilename(String name) {
        String base = (name == null || name.isBlank()) ? "report" : name.trim();
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("(^-+|-+$)", "");
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 80);
        }
        return cleaned.isBlank() ? "report" : cleaned;
    }
}
