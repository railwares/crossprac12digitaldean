package ua.edu.digitaldean.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface RequestGateway {
    List<RequestFileRow> readResource() throws IOException;

    List<RequestFileRow> read(Path path, String format) throws IOException;

    void write(Path path, String format, List<RequestFileRow> rows) throws IOException;
}