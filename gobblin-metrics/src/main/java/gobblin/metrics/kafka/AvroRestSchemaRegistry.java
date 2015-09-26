package gobblin.metrics.kafka;

import java.util.Properties;

import org.apache.avro.Schema;
import org.schemarepo.SchemaEntry;
import org.schemarepo.SchemaValidationException;
import org.schemarepo.Subject;
import org.schemarepo.client.RESTRepositoryClient;
import org.apache.hadoop.conf.Configuration;

/**
 * An implementation of SchemaRegistry for that uses schema-repo(https://github.com/schema-repo/schema-repo) to
 * manage Avro schemas.
 */
public class AvroRestSchemaRegistry {
	private RESTRepositoryClient client;
//	public static final String ETL_SCHEMA_REGISTRY_URL = "etl.schema.registry.url";
	public static final String KAFKA_SCHEMA_REGISTRY_URL = "kafka.schema.registry.url";
	public static final byte MAGIC_BYTE = 0x0;
	public static final int SCHEMA_ID_LENGTH_BYTE = 4;
	public AvroRestSchemaRegistry(Properties props) {
		client = new RESTRepositoryClient(props.getProperty(KAFKA_SCHEMA_REGISTRY_URL));
	}

	public String register(String topic, Schema schema) {
		Subject subject = client.lookup(topic);

		if (subject == null) {
			subject = client.register(topic, "org.apache.avro.repo.Validator");
		}

		try {
			return subject.register(schema.toString()).getId();
		} catch (SchemaValidationException e) {
			throw new SchemaRegistryException(e);
		}
	}

	public Schema getSchemaByID(String topic, String id) {
		Subject subject = client.lookup(topic);

		if (subject == null) {
			throw new SchemaNotFoundException("Schema not found for " + topic);
		}

		SchemaEntry entry = subject.lookupById(id);

		if (entry == null)
			throw new SchemaNotFoundException("Schema not found for " + topic
					+ " " + id);

		return Schema.parse(entry.getSchema());
	}

	public Schema getLatestSchemaByTopic(String topicName) {
		Subject subject = client.lookup(topicName);

		if (subject == null) {
			throw new SchemaNotFoundException("Schema not found for "
					+ topicName);
		}

		SchemaEntry entry = subject.latest();

		if (entry == null)
			throw new SchemaNotFoundException("Schema not found for "
					+ topicName);

		return Schema.parse(entry.getSchema());
	}
}
