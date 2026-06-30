package com.broksforge.modules.report.service;

/**
 * A rendered report ready to stream to the client.
 *
 * @param filename    suggested download filename
 * @param contentType the MIME type to set on the response
 * @param content     the rendered body
 */
public record RenderedReport(String filename, String contentType, String content) {
}
