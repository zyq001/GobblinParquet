package gobblin.source.extractor.extract.kafka;

import gobblin.configuration.WorkUnitState;
import gobblin.source.extractor.Extractor;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;

/**
 * Created by zangyq on 2015/9/26.
 */
public class SchemaRepoKafkaAvroSource extends KafkaSource<Schema, GenericRecord> {
    @Override
    public Extractor<Schema, GenericRecord> getExtractor(WorkUnitState state) throws IOException {
        return new SchemaRepoKafkaAvroExtractor(state);
    }
}