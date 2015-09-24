package gobblin.writer;

import com.google.common.base.Optional;
import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.State;
import gobblin.source.extractor.schema.Schema;
import gobblin.util.ForkOperatorUtils;
import gobblin.util.WriterUtils;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.avro.*;
import org.apache.hadoop.fs.Path;
/**
 * Created by zangyq on 2015/9/13.
 */
class ParquetHdfsDataWriter extends FsDataWriter<GenericRecord>{

    protected final AtomicLong count = new AtomicLong(0);
    private MessageType schema = null;
    private ParquetWriter writer;

    public ParquetHdfsDataWriter(State properties, String fileName, org.apache.avro.Schema schema, int numBranches, int branchId)
            throws IOException {
        super(properties, fileName, numBranches, branchId);

//        CodecFactory codecFactory =
//                WriterUtils.getCodecFactory(Optional.fromNullable(properties.getProp(
//                                ForkOperatorUtils.getPropertyNameForBranch(ConfigurationKeys.WRITER_CODEC_TYPE, numBranches, branchId))),
//                        Optional.fromNullable(properties.getProp(ForkOperatorUtils
//                                .getPropertyNameForBranch(ConfigurationKeys.WRITER_DEFLATE_LEVEL, numBranches, branchId))));

//        this.schema = schema;
//        this.groupWriter = new GenericDatumWriter<Group>();
        this.writer = this.closer.register(createDataFileWriter(fileName, schema));
    }

    @Override
    public void write(GenericRecord record) throws IOException {
//        try {
            writer.write(record);
//        } catch (InterruptedException e) {
//            throw new IOException(e);
//        }

        this.count.incrementAndGet();
    }

    @Override
    public long recordsWritten() {
        return this.count.get();
    }

    @Override
    public long bytesWritten() throws IOException {
        if (!this.fs.exists(this.outputFile)) {
        return 0;
    }

        return this.fs.getFileStatus(this.outputFile).getLen();
    }

    private ParquetWriter<GenericRecord> createDataFileWriter(String fileName, org.apache.avro.Schema schema ) throws IOException {
//        DataFileWriter<GenericRecord> writer = new DataFileWriter<GenericRecord>(this.datumWriter);
//        writer.setCodec(codecFactory);
        Configuration testConf = new Configuration();
        testConf.setBoolean(AvroReadSupport.AVRO_COMPATIBILITY, true);
        testConf.setBoolean("parquet.avro.add-list-element-records", false);
        testConf.setBoolean("parquet.avro.write-old-list-structure", false);
System.out.println("fffffffffffffffffffffffffffffffffffileNeme OUTPUT:    " + fileName);
        return AvroParquetWriter
                .<GenericRecord>builder(this.stagingFile)
                .withSchema(schema)
                .withConf(testConf)
                .build();
//        return new ParquetWriter<GenericRecord>(file,new GroupWriteSupport(schema));
        // Open the file and return the DataFileWriter
//        return writer.create(this.schema, this.stagingFileOutputStream);

    }
}
