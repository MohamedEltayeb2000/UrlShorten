package com.example.shortener.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.support.Requirement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-006")
class RunPersistenceTest {

    @Autowired AgentRunRepository runs;
    @Autowired RunNodeRepository nodes;
    @Autowired NodeTransitionRepository transitions;
    @Autowired JdbcTemplate jdbc;

    private AgentRun newRun(String req) {
        return runs.save(new AgentRun("wf", "LINK", req, req.toLowerCase()));
    }

    @Test
    void savesRunNodesAndTransitionsAndReadsThemBack() {
        AgentRun run = newRun("Build It");
        Instant t = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        run.setStatus(RunStatus.RUNNING);
        run.setPlanVersion(2);
        run.setStartedAt(t);
        runs.save(run);

        RunNode a = nodes.save(new RunNode(run.getId(), "a", "Node A", List.of(), false));
        RunNode b = new RunNode(run.getId(), "b", "Node B", List.of("a", "x"), true);
        b.setStatus(NodeStatus.FAILED);
        b.setAttemptCount(3);
        b.setStartedAt(t);
        b.setCompletedAt(t.plusSeconds(1));
        b.setErrorCode("E1");
        b.setErrorSummary("boom");
        b = nodes.save(b);
        transitions.save(new NodeTransition(b.getId(), NodeStatus.RUNNING, NodeStatus.FAILED, t, "boom"));

        AgentRun loaded = runs.findById(run.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(RunStatus.RUNNING);
        assertThat(loaded.getPlanVersion()).isEqualTo(2);
        assertThat(loaded.getRawRequirement()).isEqualTo("Build It");
        assertThat(loaded.getStartedAt()).isEqualTo(t);
        assertThat(loaded.getCompletedAt()).isNull();

        List<RunNode> all = nodes.findByRunIdOrderById(run.getId());
        assertThat(all).extracting(RunNode::getNodeId).containsExactly("a", "b");
        assertThat(all.get(0).dependsOnIds()).isEmpty();
        assertThat(all.get(0).getStatus()).isEqualTo(NodeStatus.PENDING);
        RunNode lb = nodes.findByRunIdAndNodeId(run.getId(), "b").orElseThrow();
        assertThat(lb.dependsOnIds()).containsExactly("a", "x");
        assertThat(lb.isRequiresApproval()).isTrue();
        assertThat(lb.getAttemptCount()).isEqualTo(3);
        assertThat(lb.getStartedAt()).isEqualTo(t);
        assertThat(lb.getCompletedAt()).isEqualTo(t.plusSeconds(1));
        assertThat(lb.getErrorCode()).isEqualTo("E1");
        assertThat(lb.getErrorSummary()).isEqualTo("boom");

        List<NodeTransition> ts = transitions.findByRunNodeIdOrderByIdAsc(b.getId());
        assertThat(ts).hasSize(1);
        assertThat(ts.get(0).getFromStatus()).isEqualTo(NodeStatus.RUNNING);
        assertThat(ts.get(0).getToStatus()).isEqualTo(NodeStatus.FAILED);
        assertThat(ts.get(0).getOccurredAt()).isEqualTo(t);
        assertThat(ts.get(0).getReason()).isEqualTo("boom");
        assertThat(transitions.findByRunNodeIdOrderByIdAsc(a.getId())).isEmpty();
    }

    @Test
    void duplicateNodeIdInSameRunIsRejected() {
        AgentRun run = newRun("dup");
        nodes.saveAndFlush(new RunNode(run.getId(), "a", "A", List.of(), false));
        assertThatThrownBy(() -> nodes.saveAndFlush(new RunNode(run.getId(), "a", "A2", List.of(), false)))
                .isInstanceOf(DataIntegrityViolationException.class);
        AgentRun other = newRun("other");
        nodes.saveAndFlush(new RunNode(other.getId(), "a", "A", List.of(), false));
    }

    @Test
    void runsArePagedNewestFirst() {
        Instant base = Instant.now().plusSeconds(100_000).truncatedTo(ChronoUnit.MILLIS);
        AgentRun old = newRun("old");
        old.setCreatedAt(base);
        AgentRun mid = newRun("mid");
        mid.setCreatedAt(base.plusSeconds(1));
        AgentRun recent = newRun("recent");
        recent.setCreatedAt(base.plusSeconds(2));
        runs.saveAll(List.of(old, mid, recent));

        List<AgentRun> page1 = runs.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 2));
        assertThat(page1).extracting(AgentRun::getId).containsExactly(recent.getId(), mid.getId());
        List<AgentRun> page2 = runs.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(1, 2));
        assertThat(page2.get(0).getId()).isEqualTo(old.getId());
    }

    @Test
    void transitionsComeBackInInsertionOrder() {
        AgentRun run = newRun("order");
        RunNode n = nodes.save(new RunNode(run.getId(), "a", "A", List.of(), false));
        Instant t = Instant.now();
        NodeStatus[] path = {NodeStatus.PENDING, NodeStatus.READY, NodeStatus.RUNNING, NodeStatus.SUCCEEDED};
        for (int i = 1; i < path.length; i++) {
            // occurred_at deliberately identical: order must come from insertion, not time
            transitions.save(new NodeTransition(n.getId(), path[i - 1], path[i], t, null));
        }
        assertThat(transitions.findByRunNodeIdOrderByIdAsc(n.getId()))
                .extracting(NodeTransition::getToStatus)
                .containsExactly(NodeStatus.READY, NodeStatus.RUNNING, NodeStatus.SUCCEEDED);
    }

    @Test
    void nodeStatusSurvivesFreshLoad() {
        AgentRun run = newRun("restart");
        RunNode n = nodes.save(new RunNode(run.getId(), "a", "A", List.of(), false));
        n.setStatus(NodeStatus.WAITING_FOR_APPROVAL);
        nodes.save(n);
        // no surrounding transaction: each repository call is its own, so this is a fresh load
        RunNode fresh = nodes.findById(n.getId()).orElseThrow();
        assertThat(fresh).isNotSameAs(n);
        assertThat(fresh.getStatus()).isEqualTo(NodeStatus.WAITING_FOR_APPROVAL);
    }

    @Test
    void flywayAppliedV3() {
        Integer count = jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '3' and \"success\" = true", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
