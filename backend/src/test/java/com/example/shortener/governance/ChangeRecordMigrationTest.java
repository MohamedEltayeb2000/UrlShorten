package com.example.shortener.governance;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-033")
class ChangeRecordMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ApprovalRepository approvals;

    @Test
    void flywayV6AddedTheChangeRecordColumns() {
        List<String> cols = jdbc.queryForList(
                "select lower(column_name) from information_schema.columns where lower(table_name) = 'approval'", String.class);
        for (String c : List.of("risk_class", "change_ticket", "risk_justification", "proposed_commands", "proposed_migrations")) {
            assertTrue(cols.contains(c), "missing column " + c);
        }
        Integer applied = jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '6' and \"success\" = true", Integer.class);
        assertEquals(1, applied);
    }

    @Test
    void theChangeRecordRoundTripsAndTheRiskClassDefaultsFromTheKind() {
        Approval high = new Approval(9_001L, "IMPLEMENTATION", Approval.Kind.HIGH_IMPACT_ACTION, "r", 1);
        Approval clarification = new Approval(9_001L, "REQUIREMENTS_ANALYSIS", Approval.Kind.CLARIFICATION, "r", 1);
        assertEquals("HIGH", high.getRiskClass());
        assertEquals("MEDIUM", clarification.getRiskClass());
        high.setProposals("[\"mvn -q test\"]", "[]");
        high.recordChange("CHG-7", "justified enough");
        Approval saved = approvals.save(high);
        Approval read = approvals.findById(saved.getId()).orElseThrow();
        assertEquals("CHG-7", read.getChangeTicket());
        assertEquals("[\"mvn -q test\"]", read.getProposedCommands());
        assertEquals("HIGH", read.getRiskClass());
    }
}
