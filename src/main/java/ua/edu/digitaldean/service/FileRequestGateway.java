package ua.edu.digitaldean.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class FileRequestGateway implements RequestGateway {
    private final RequestFiles files;

    public FileRequestGateway() {
        this(new RequestFiles());
    }

    public FileRequestGateway(RequestFiles files) {
        this.files = Objects.requireNonNull(files, "Компонент RequestFiles не може бути null.");
    }

    @Override
    public List<RequestFileRow> readResource() throws IOException {
        return files.readResource();
    }

    @Override
    public List<RequestFileRow> read(Path path, String format) throws IOException {
        return files.read(path, format);
    }

    @Override
    public void write(Path path, String format, List<RequestFileRow> rows) throws IOException {
        files.write(path, format, rows);
    }
}