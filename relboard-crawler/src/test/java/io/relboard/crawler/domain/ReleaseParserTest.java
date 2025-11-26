package io.relboard.crawler.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ReleaseParserTest {

    private final ReleaseParser parser = new ReleaseParser();

    @Test
    void extractTags_returnsMatchingTagsIgnoringCase() {
        var content = "Breaking change: new FEAT added with bug fix and docs update";

        Set<ReleaseTagType> tags = parser.extractTags(content);

        assertThat(tags).containsExactlyInAnyOrder(
                ReleaseTagType.BREAKING,
                ReleaseTagType.FEAT,
                ReleaseTagType.FIX,
                ReleaseTagType.DOCS
        );
    }

    @Test
    void extractTags_returnsEmptyWhenContentBlank() {
        assertThat(parser.extractTags(" ")).isEmpty();
    }
}
