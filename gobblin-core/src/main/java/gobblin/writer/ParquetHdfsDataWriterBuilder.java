package gobblin.writer;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import gobblin.configuration.State;
import org.apache.avro.Schema;
import gobblin.util.WriterUtils;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.example.data.Group;

import java.io.IOException;

/**
 * Created by zangyq on 2015/9/13.
 */
public class ParquetHdfsDataWriterBuilder extends DataWriterBuilder<Schema, GenericRecord>{



//    @Override
    public DataWriter<GenericRecord> build() throws IOException {

        Preconditions.checkNotNull(this.destination);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(this.writerId));
        Preconditions.checkNotNull(this.schema);
        Preconditions.checkArgument(this.format == WriterOutputFormat.PARQUET);
//        AvroSchemaConverter avroSchemaConverter = new AvroSchemaConverter(conf);
//        MessageType schema = avroSchemaConverter.convert(avroSchema);
        switch (this.destination.getType()) {
            case HDFS:
                State properties = this.destination.getProperties();

                String fileName = WriterUtils
                        .getWriterFileName(properties, this.branches, this.branch, this.writerId, this.format.getExtension());

//                return new AvroHdfsDataWriter(properties, fileName, this.schema, this.branches, this.branch);
            return new ParquetHdfsDataWriter(properties, fileName, this.schema, this.branches, this.branch);

//            case KAFKA:
//                return new AvroKafkaDataWriter();
            default:
                throw new RuntimeException("Unknown destination type: " + this.destination.getType());
        }
    }
}
