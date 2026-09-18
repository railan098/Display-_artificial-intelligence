package dev.agentmeter.service;

import dev.agentmeter.model.AgentDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRegistryTest {
    @Test void bringsBuiltInAgentsWithoutPluginFile(@TempDir Path dir) {
        AgentRegistry registry = new AgentRegistry(null, dir.resolve("agents.json"));
        assertNotNull(registry.byId(AgentRegistry.CODEX));
        assertNotNull(registry.byId(AgentRegistry.CLAUDE));
        assertEquals(List.of(AgentRegistry.CODEX, AgentRegistry.CLAUDE),
                registry.primary().stream().map(AgentDefinition::id).toList());
        assertTrue(registry.problems().isEmpty());
    }

    @Test void loadsUserDefinedAgent(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("agents.json");
        Files.writeString(file, """
                {"agents":[{"id":"meu","label":"Meu Agente","match":{"names":["meu-agente"]}}]}
                """);
        AgentRegistry registry = new AgentRegistry(null, file);
        AgentDefinition agent = registry.byId("meu");
        assertNotNull(agent);
        assertEquals("Meu Agente", agent.label());
        assertEquals(List.of("meu-agente"), agent.match().names());
        assertTrue(registry.problems().isEmpty());
    }

    @Test void reportsDefinitionWithoutRules(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("agents.json");
        Files.writeString(file, """
                {"agents":[{"id":"vazio","label":"Sem regra"}]}
                """);
        AgentRegistry registry = new AgentRegistry(null, file);
        assertEquals(1, registry.problems().size());
        assertTrue(registry.problems().get(0).contains("vazio"));
    }

    @Test void userDefinitionExtendsBuiltInInsteadOfReplacingIt(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("agents.json");
        Files.writeString(file, """
                {"agents":[{"id":"codex","label":"Codex","match":{"names":["meu-codex"]}}]}
                """);
        AgentRegistry registry = new AgentRegistry(null, file);
        List<String> names = registry.byId(AgentRegistry.CODEX).match().names();
        assertTrue(names.contains("codex"), "regra interna preservada");
        assertTrue(names.contains("meu-codex"), "regra do usuário aplicada");
    }

    @Test void reloadsWhenFileChanges(@TempDir Path dir) throws IOException, InterruptedException {
        Path file = dir.resolve("agents.json");
        AgentRegistry registry = new AgentRegistry(null, file);
        assertFalse(registry.refresh());
        Thread.sleep(10);
        Files.writeString(file, """
                {"agents":[{"id":"novo","match":{"names":["novo"]}}]}
                """);
        assertTrue(registry.refresh());
        assertNotNull(registry.byId("novo"));
    }
}
