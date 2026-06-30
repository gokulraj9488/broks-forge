package com.broksforge.modules.agent.domain;

import com.broksforge.common.persistence.JsonMetadataConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The capabilities an agent supports. Embedded into {@code agents}.
 *
 * <p>Well-known capabilities are explicit, typed boolean columns so they can be
 * indexed and filtered. {@link #customMetadata} is an open JSON map that lets new
 * or product-specific capabilities be recorded without a schema migration — the
 * extensibility seam future modules build on.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class AgentCapabilities {

    @Column(name = "cap_streaming", nullable = false)
    private boolean streaming = false;

    @Column(name = "cap_memory", nullable = false)
    private boolean memory = false;

    @Column(name = "cap_rag", nullable = false)
    private boolean rag = false;

    @Column(name = "cap_tool_calling", nullable = false)
    private boolean toolCalling = false;

    @Column(name = "cap_structured_output", nullable = false)
    private boolean structuredOutput = false;

    @Column(name = "cap_reasoning", nullable = false)
    private boolean reasoning = false;

    @Column(name = "cap_multi_agent", nullable = false)
    private boolean multiAgent = false;

    @Convert(converter = JsonMetadataConverter.class)
    @Column(name = "custom_metadata", columnDefinition = "text")
    private Map<String, Object> customMetadata = new LinkedHashMap<>();
}
