package gobblin.source.extractor.extract.kafka;

import com.google.common.base.Optional;
import gobblin.configuration.WorkUnitState;
import gobblin.metrics.kafka.KafkaAvroSchemaRegistry;
import gobblin.metrics.kafka.SchemaNotFoundException;
import gobblin.source.extractor.DataRecordException;
import gobblin.util.AvroUtils;
import kafka.message.MessageAndOffset;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by zangyq on 2015/9/26.
 */
public class SchemaRepoKafkaAvroExtractor extends KafkaExtractor<Schema, GenericRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAvroExtractor.class);
    private static final Schema DEFAULT_SCHEMA = SchemaBuilder.record("DefaultSchema").fields().name("header")
            .type(SchemaBuilder.record("header").fields().name("time").type("long").withDefault(0).endRecord()).noDefault()
            .endRecord();

    private final Optional<Schema> schema;
    private final AvroRestSchemaRegistry schemaRepoRegistry;
    private final Optional<GenericDatumReader<GenericData.Record>> reader;


    public SchemaRepoKafkaAvroExtractor(WorkUnitState state) {
        super(state);
        this.schemaRepoRegistry = new AvroRestSchemaRegistry(state.getProperties());

        this.schema = Optional.fromNullable(this.schemaRepoRegistry.getLatestSchemaByTopic());
        if (this.schema.isPresent()) {
            this.reader = Optional.of(new GenericDatumReader<GenericData.Record>(this.schema.get()));
        } else {
            this.reader = Optional.absent();
        }
    }


    @Override
    public GenericRecord readRecordImpl(GenericRecord reuse) throws DataRecordException, IOException {
        if (!this.schema.isPresent()) {
            return null;
        }
        return super.readRecordImpl(reuse);
    }

    @Override
    public Schema getSchema() {
        return this.schema.or(DEFAULT_SCHEMA);
    }

    /**
     * convert byte array to int
     * @author zyq001
     *
     * */
    public int toInt(byte[] bRefArr) {
        int iOutcome = 0;
        byte bLoop;

        for ( int i =0; i<4 ; i++) {
            bLoop = bRefArr[i];
            iOutcome+= (bLoop & 0xFF) << (8 * i);

        }

        return iOutcome;
    }

    @Override
    protected GenericRecord decodeRecord(MessageAndOffset messageAndOffset) throws SchemaNotFoundException, IOException {
        byte[] payload = getBytes(messageAndOffset.message().payload());
//        System.out.println("payload.length:  " + payload.length);
//        System.out.println("payload[0]: " + payload[0] + "  == 0x0? :" + payload[0] != KafkaAvroSchemaRegistry.MAGIC_BYTE + "  magic: " + KafkaAvroSchemaRegistry.MAGIC_BYTE);
        if (payload[0] != AvroRestSchemaRegistry.MAGIC_BYTE) {
            throw new RuntimeException(String.format("Unknown magic byte for partition %s", this.getCurrentPartition()));
        }

        byte[] schemaIdByteArray = Arrays.copyOfRange(payload, 1, 1 + AvroRestSchemaRegistry.SCHEMA_ID_LENGTH_BYTE);
        //byte arr to int camus use an int as schemaid
        String schemaId = "" + toInt(schemaIdByteArray);
//    String schemaId = Integer.toString(schemaIdByteArray.)
        Schema schema = null;
//    System.out.println("schemaId: " + schemaId);
        schema = this.schemaRegistry.getSchemaById(this.topicName, schemaId);

//    schema = this.schemaRegistry.getLatestSchemaByTopic("realtime");
        System.out.println("DDDDDDDDDDDDDDDDDDDDDDDDDDcode: schema: " + schema);
        reader.get().setSchema(schema);
        System.out.println("Reader.schema: " + reader.get().getSchema());
        System.out.println("pppppppppppppppppppp:" + new String(payload, "utf-8"));
        Decoder binaryDecoder =
                DecoderFactory.get().binaryDecoder(payload, 1 + KafkaAvroSchemaRegistry.SCHEMA_ID_LENGTH_BYTE,
                        payload.length - 1 - KafkaAvroSchemaRegistry.SCHEMA_ID_LENGTH_BYTE, null);
        System.out.println("binaryDecoder  " + binaryDecoder);
        try {
            GenericDatumReader<GenericData.Record> rr = reader.get();
            System.out.println("rrrrrrrrrrrrrrrrrrr:   " + rr.getData());
            GenericRecord record = reader.get().read(null, binaryDecoder);
            System.out.println("GGGGGGGGGGGrecord.get(0) :  " + record.get(0));
            System.out.println("GGGGGGGGGGGGGrecord.get(\"city\") :  " + record.get("city"));
            if (!record.getSchema().equals(this.schema.get())) {
                record = AvroUtils.convertRecordSchema(record, this.schema.get());
            }
            System.out.println("record.get(0) :  " + record.get(0));
            System.out.println("record.get(\"city\") :  " + record.get("city"));
            return record;
        }
        catch (IOException e) {
            System.out.println("eeeeeeeeeeexception:  " + e.toString());
//      e.printStackTrace();
            LOG.error(String.format("Error during decoding record for partition %s: ", this.getCurrentPartition()));
            System.out.println("exception:  " + e + "\nstack: \n" + e.getStackTrace());
            e.printStackTrace();
            throw e;
        }
    }
}
