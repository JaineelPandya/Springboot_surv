package com.surv.platform.document;

import com.surv.platform.domain.ClassificationRule;
import com.surv.platform.domain.Document;
import com.surv.platform.repository.ClassificationRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rule-based classification engine.
 * Evaluates keyword, MIME-type pattern, and metadata matchers against document signals.
 * Rules are evaluated in ascending priority order; first match wins.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationEngine {

    private final ClassificationRuleRepository ruleRepository;

    /**
     * Evaluate all enabled rules against the given signals.
     *
     * @param title            document title
     * @param detectedMimeType MIME type from Tika content inspection
     * @param metadata         document JSONB metadata map
     * @return first matching rule and its target level, or empty if no rule matches
     */
    public Optional<ClassificationRuleMatch> evaluate(String title,
                                                       String detectedMimeType,
                                                       Map<String, Object> metadata) {
        List<ClassificationRule> rules = ruleRepository.findByEnabledTrueOrderByPriorityAsc();

        for (ClassificationRule rule : rules) {
            if (matches(rule, title, detectedMimeType, metadata)) {
                log.debug("Classification rule '{}' matched: title='{}', mime='{}'",
                        rule.getName(), title, detectedMimeType);
                return Optional.of(new ClassificationRuleMatch(rule.getTargetLevel(), rule));
            }
        }
        return Optional.empty();
    }

    private boolean matches(ClassificationRule rule, String title,
                             String mimeType, Map<String, Object> metadata) {
        // Keyword match against document title (case-insensitive)
        if (rule.getKeywords() != null && !rule.getKeywords().isEmpty()) {
            String lowerTitle = title != null ? title.toLowerCase() : "";
            boolean keywordFound = rule.getKeywords().stream()
                    .anyMatch(kw -> lowerTitle.contains(kw.toLowerCase()));
            if (keywordFound) return true;
        }

        // MIME type pattern match (supports wildcard *)
        if (rule.getMimeTypePatterns() != null && !rule.getMimeTypePatterns().isEmpty()
                && mimeType != null && !mimeType.isBlank()) {
            boolean mimeMatched = rule.getMimeTypePatterns().stream()
                    .anyMatch(pattern -> mimeType.matches(pattern.replace("*", ".*")));
            if (mimeMatched) return true;
        }

        // Metadata exact-value matchers (all conditions must match)
        if (rule.getMetadataMatchers() != null && !rule.getMetadataMatchers().isEmpty()
                && metadata != null && !metadata.isEmpty()) {
            boolean allMatch = rule.getMetadataMatchers().entrySet().stream().allMatch(entry -> {
                Object val = metadata.get(entry.getKey());
                return val != null && val.toString().equals(entry.getValue().toString());
            });
            if (allMatch) return true;
        }

        return false;
    }

    public record ClassificationRuleMatch(Document.SensitivityLevel level, ClassificationRule rule) {}
}
