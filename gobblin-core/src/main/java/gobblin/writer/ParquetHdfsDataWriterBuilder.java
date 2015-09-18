package gobblin.writer;

import gobblin.source.extractor.schema.Schema;
import org.apache.parquet.example.data.Group;

import java.io.IOException;

/**
 * Created by zangyq on 2015/9/13.
 */
public class ParquetHdfsDataWriterBuilder extends DataWriterBuilder<Schema, Group>{
    @Override
    public DataWriter<Group> build() throws IOException {
        return null;
    }
}
