package com.broksforge.fxp.copilot;

/** The engineering intent behind a natural-language question — each maps to one FKGE proof. */
public enum Intent {
    WHY("why it exists / was decided"),
    PROVENANCE("where it came from"),
    IMPACT("what it affects"),
    DEPENDENCIES("what it needs"),
    ROOT_CAUSE("what caused it"),
    CONFIDENCE("how confident we are"),
    EVIDENCE("what supports it"),
    WHY_IN_PRODUCTION("why it is in production");

    private final String human;

    Intent(String human) {
        this.human = human;
    }

    public String human() {
        return human;
    }
}
