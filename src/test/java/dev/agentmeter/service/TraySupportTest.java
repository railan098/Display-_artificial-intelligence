package dev.agentmeter.service;

import dev.agentmeter.model.AgentDefinition;
import dev.agentmeter.model.AgentDefinition.MatchRule;
import dev.agentmeter.model.AgentDefinition.UsageSource;
import dev.agentmeter.model.UsageSnapshot;
import dev.agentmeter.model.UsageWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraySupportTest {
    @Test void tooltipShowsFreshFiveHourUsageAndHidesOldClaudeCache() {
        AgentDefinition codex = agent("codex", "Codex", UsageSource.CODEX_ROLLOUT);
        AgentDefinition claude = agent("claude", "Claude Code", UsageSource.CLAUDE_CACHE);
        UsageSnapshot codexUsage = usage("codex", 31, Instant.now());
        UsageSnapshot oldClaudeUsage = usage("claude", 0, Instant.now().minus(2, ChronoUnit.MINUTES));

        String tooltip = TraySupport.buildUsageTooltip(
                Map.of("codex", codexUsage, "claude", oldClaudeUsage), List.of(codex, claude));

        assertEquals("Agent Meter\nCodex 5h: 69% restante\nClaude 5h: — (use /usage)", tooltip);
    }

    @Test void tooltipShowsClaudeImmediatelyAfterOfficialRefresh() {
        AgentDefinition claude = agent("claude", "Claude Code", UsageSource.CLAUDE_CACHE);
        String tooltip = TraySupport.buildUsageTooltip(
                Map.of("claude", usage("claude", 12, Instant.now())), List.of(claude));
        assertEquals("Agent Meter\nClaude 5h: 88% restante", tooltip);
    }

    private static UsageSnapshot usage(String id, double used, Instant observed) {
        return new UsageSnapshot(id,
                List.of(new UsageWindow("Limite de 5 horas", used,
                        Instant.now().plus(4, ChronoUnit.HOURS), 300)), observed, "test");
    }

    private static AgentDefinition agent(String id, String label, UsageSource source) {
        return new AgentDefinition(id, label, "test", label.substring(0, 1), "#ffffff",
                new MatchRule(List.of(id), List.of(), List.of(), List.of()), source, "", true, "test");
    }
}
