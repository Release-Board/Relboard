package io.relboard.crawler.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ReleaseParser {

    private static final Map<ReleaseTagType, Pattern> TAG_PATTERNS = Map.of(
            ReleaseTagType.BREAKING, Pattern.compile("\\bbreaking\\b", Pattern.CASE_INSENSITIVE),
            ReleaseTagType.SECURITY, Pattern.compile("\\bsecurity\\b", Pattern.CASE_INSENSITIVE),
            ReleaseTagType.FEAT, Pattern.compile("\\b(feat|feature)\\b", Pattern.CASE_INSENSITIVE),
            ReleaseTagType.FIX, Pattern.compile("\\b(fix|bug)\\b", Pattern.CASE_INSENSITIVE),
            ReleaseTagType.DOCS, Pattern.compile("\\b(docs|documentation)\\b", Pattern.CASE_INSENSITIVE)
    );

    public Set<ReleaseTagType> extractTags(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }

        EnumSet<ReleaseTagType> tags = EnumSet.noneOf(ReleaseTagType.class);
        TAG_PATTERNS.forEach((type, pattern) -> {
            if (pattern.matcher(content).find()) {
                tags.add(type);
            }
        });
        return tags;
    }
}
