package dev.pdv.api.inventory.application;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.inventory.application.InventoryModels.Balance;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Strong entity-tag comparison; opaque tags are never interpreted as trusted versions. */
public record InventoryPrecondition(boolean present, boolean wildcard, List<String> tags, String canonical) {
    private static final Pattern ENTITY_TAG = Pattern.compile("(?:W/)?\"[\\x21\\x23-\\x7E\\x80-\\xFF]*\"");

    public static String tag(Balance balance) { return "\"inventory-" + balance.productId() + "-v" + balance.version() + "\""; }
    public static InventoryPrecondition parse(String raw) {
        if (raw == null) { return new InventoryPrecondition(false, false, List.of(), ""); }
        if (raw.length() > 4096) { throw invalid(); }
        String value = raw.strip();
        if (value.equals("*")) { return new InventoryPrecondition(true, true, List.of(), "*"); }
        var tags = new ArrayList<String>(); int offset = 0;
        while (offset < value.length()) {
            offset = whitespace(value, offset);
            if (offset == value.length()) { break; }
            if (value.charAt(offset) == ',') { offset++; continue; }
            var matcher = ENTITY_TAG.matcher(value).region(offset, value.length());
            if (!matcher.lookingAt()) { throw invalid(); }
            tags.add(matcher.group()); if (tags.size() > 32) { throw invalid(); }
            offset = whitespace(value, matcher.end());
            if (offset < value.length()) { if (value.charAt(offset) != ',') { throw invalid(); } offset++; }
        }
        if (tags.isEmpty()) { throw invalid(); }
        var canonical = tags.stream().distinct().sorted().toList();
        return new InventoryPrecondition(true, false, canonical, String.join(",", canonical));
    }
    public void verify(Balance balance) {
        if (present && !wildcard && !tags.contains(tag(balance))) { throw new ApiFailure(412, "precondition_failed"); }
    }
    public long resolve(Balance balance, Long bodyVersion) {
        verify(balance);
        if (bodyVersion != null && bodyVersion != balance.version()) { throw new ApiFailure(409, "version_conflict"); }
        if (bodyVersion == null && (!present || wildcard)) { throw new ApiFailure(428, "version_required"); }
        return bodyVersion == null ? balance.version() : bodyVersion;
    }
    private static int whitespace(String value, int offset) {
        while (offset < value.length() && (value.charAt(offset) == ' ' || value.charAt(offset) == '\t')) { offset++; }
        return offset;
    }
    private static ApiFailure invalid() { return new ApiFailure(400, "invalid_request"); }
}
