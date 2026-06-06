package egovframework.example.complaint.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresMigrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	@Test
	void appliesCompleteTrustWorkflowSchemaToPostgres() throws Exception {
		Flyway flyway = Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration")
				.load();

		MigrateResult result = flyway.migrate();
		assertThat(result.targetSchemaVersion).isEqualTo("18");

		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement();
				ResultSet tables = statement.executeQuery("""
						select count(*)
						from information_schema.tables
						where table_schema = 'public'
						  and table_name in (
						    'processing_jobs',
						    'evidence_snapshots',
						    'claim_evidence_links',
						    'human_reviews',
						    'workflow_audit_events',
						    'complaint_sensitive_payloads'
						  )
						""")) {
			assertThat(tables.next()).isTrue();
			assertThat(tables.getInt(1)).isEqualTo(6);
		}
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement();
				ResultSet legacyUsers = statement.executeQuery("""
						select count(*)
						from information_schema.tables
						where table_schema = 'public'
						  and table_name = 'api_users'
						""")) {
			assertThat(legacyUsers.next()).isTrue();
			assertThat(legacyUsers.getInt(1)).isZero();
		}
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement();
				ResultSet synthetic = statement.executeQuery("""
						select
						  (select count(*) from organization_units where synthetic_demo = true),
						  (select count(*) from assignment_rules where synthetic_demo = true)
						""")) {
			assertThat(synthetic.next()).isTrue();
			assertThat(synthetic.getInt(1)).isEqualTo(3);
			assertThat(synthetic.getInt(2)).isEqualTo(3);
		}
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement();
				ResultSet legalBasis = statement.executeQuery("""
						select count(*)
						from information_schema.columns
						where table_schema = 'public'
						  and table_name = 'evidence_snapshots'
						  and column_name = 'legal_basis'
						""")) {
			assertThat(legalBasis.next()).isTrue();
			assertThat(legalBasis.getInt(1)).isEqualTo(1);
		}
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement();
				ResultSet claimSourceIds = statement.executeQuery("""
						select count(*)
						from information_schema.columns
						where table_schema = 'public'
						  and table_name = 'draft_claims'
						  and column_name = 'source_document_ids'
						""")) {
			assertThat(claimSourceIds.next()).isTrue();
			assertThat(claimSourceIds.getInt(1)).isEqualTo(1);
		}
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement();
				ResultSet sourceVersions = statement.executeQuery("""
						select count(*)
						from information_schema.columns
						where table_schema = 'public'
						  and column_name = 'source_version'
						  and table_name in ('knowledge_documents', 'evidence_snapshots')
						""")) {
			assertThat(sourceVersions.next()).isTrue();
			assertThat(sourceVersions.getInt(1)).isEqualTo(2);
		}
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement();
				ResultSet operationalColumns = statement.executeQuery("""
						select count(*)
						from information_schema.columns
						where table_schema = 'public'
						  and (
						    (table_name = 'processing_jobs' and column_name = 'payload_reference')
						    or (table_name = 'attachment_analysis' and column_name = 'derived_storage_reference')
						    or (table_name = 'source_registry' and column_name = 'stale_after')
						  )
						""")) {
			assertThat(operationalColumns.next()).isTrue();
			assertThat(operationalColumns.getInt(1)).isEqualTo(3);
		}
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement();
				ResultSet dataMartTables = statement.executeQuery("""
						select count(*)
						from information_schema.tables
						where table_schema = 'public'
						  and table_name in (
						    'data_mart_ingestion_runs',
						    'data_mart_raw_records',
						    'data_mart_normalized_records',
						    'data_mart_load_errors'
						  )
						""")) {
			assertThat(dataMartTables.next()).isTrue();
			assertThat(dataMartTables.getInt(1)).isEqualTo(4);
		}
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement();
				ResultSet spatialDataMartTables = statement.executeQuery("""
						select count(*)
						from information_schema.tables
						where table_schema = 'public'
						  and table_name in (
						    'spatial_source_registry',
						    'spatial_admin_boundaries',
						    'spatial_address_points',
						    'spatial_road_segments',
						    'spatial_facilities',
						    'spatial_parking_restrictions',
						    'spatial_location_resolution_runs',
						    'spatial_location_candidates'
						  )
						""")) {
			assertThat(spatialDataMartTables.next()).isTrue();
			assertThat(spatialDataMartTables.getInt(1)).isEqualTo(8);
		}
	}
}
