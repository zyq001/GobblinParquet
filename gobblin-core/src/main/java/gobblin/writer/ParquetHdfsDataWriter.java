package gobblin.writer;

import gobblin.source.extractor.schema.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.example.data.Group;

import java.io.IOException;

/**
 * Created by zangyq on 2015/9/13.
 */
class ParquetHdfsDataWriter extends FsDataWriter<Group>{


    public ParquetHdfsDataWriter(){
//        super();
    }

    @Override
    public void write(Group record) throws IOException {

    }

    @Override
    public long recordsWritten() {
        return 0;
    }

    @Override
    public long bytesWritten() throws IOException {
        return 0;
    }
}
