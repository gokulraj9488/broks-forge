package com.broksforge.fkge.search;

import com.broksforge.kernel.api.NodeId;

import java.util.List;

/** A recurring structural shape: a neighborhood signature shared by two or more nodes. */
public record Pattern(String signature, List<NodeId> members) {

    public Pattern {
        members = List.copyOf(members);
    }

    public int count() {
        return members.size();
    }
}
