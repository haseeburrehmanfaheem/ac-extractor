package com.uwaterloo.datadriven.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileUtils {
    public static List<String> findFileNamesFromParentPath(String inPath, String filenameWithExt) throws IOException {
        if (inPath.endsWith(filenameWithExt))
            return List.of(inPath);

        File inFile = new File(inPath);
        if (!inFile.isDirectory())
            return List.of();

        return Files.find(
                Paths.get(inFile.getAbsolutePath()),
                Integer.MAX_VALUE,
                (filePath, fileAttr) -> filePath.getFileName().toString().endsWith(filenameWithExt)
        ).map(p -> p.toAbsolutePath().toString()).toList();
    }

    public static List<Path> retrievePathsMatchingExtFromParent(String parentPath, String extToMatch) throws IOException {
        Stream<Path> opStream = Files.find(Paths.get(parentPath),
                Integer.MAX_VALUE,
                (filePath, fileAttr) -> filePath.getFileName().toString().endsWith(extToMatch));

        return opStream.collect(Collectors.toList());
    }
}
