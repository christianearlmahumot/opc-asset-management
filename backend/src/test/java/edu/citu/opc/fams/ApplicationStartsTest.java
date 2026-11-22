package edu.citu.opc.fams;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The whole application starts on a fresh PostgreSQL: every Flyway migration runs and Hibernate's
 * schema validation accepts every entity. Catches a broken deploy before Render does.
 */
@SpringBootTest
class ApplicationStartsTest {

    private static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) throws IOException {
        if (pg == null) pg = EmbeddedPostgres.builder().start();
        r.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
        r.add("fams.storage.type", () -> "local");
    }

    @AfterAll
    static void stop() throws IOException {
        if (pg != null) pg.close();
    }

    @Autowired
    ApplicationContext context;

    @Test
    void starts() {
        assertNotNull(context.getBean(edu.citu.opc.fams.importer.ImportService.class));
        assertNotNull(context.getBean(edu.citu.opc.fams.report.ExportService.class));
    }
}
