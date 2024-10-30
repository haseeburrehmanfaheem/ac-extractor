package com.uwaterloo.datadriven.utils;


import com.opencsv.CSVWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CsvUtils {
//    public static final String[] csvHeadersFields = new String[] {"ID", "Type", "Parent", "Immediate Parent", "Operations",  "Member ID", "Member Type", "Member Parent", "Member Protection"};
    public static final String[] csvHeadersFields = new String[] {"ID", "Parent", "Immediate Parent","Type", "Normalised Type", "Index::Value Type",  "Path", "Protection", "Member::<Type>", "Member::<Type>", "Member::<Type>"};
    public static final int NEXT_ROW_START_INDEX = csvHeadersFields.length - 1;
    public static final int SUBSEQUENT_ROW_SIZE = 4;
    public static final int INITIAL_ROW_SIZE = 4;

    public static void writeToCsv(String fileName, List<String[]> rows) {
        try {
            Path fp = Path.of(PropertyUtils.getOutPath(), fileName);
            Files.createDirectories(fp.getParent());
            Files.deleteIfExists(fp);
            CSVWriter csvWriter = new CSVWriter(Files.newBufferedWriter(fp));
            csvWriter.writeAll(rows);
            csvWriter.close();
        } catch (IOException e) {
            throw new RuntimeException("Error writing CSV", e);
        }
    }
}
