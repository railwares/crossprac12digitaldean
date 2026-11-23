package ua.edu.digitaldean.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class InMemoryGateway implements RequestGateway {
    private List<RequestFileRow> rowsToReturn = List.of();
    private IOException readFailure;
    private IOException writeFailure;
    private int readCalls;
    private int writeCalls;
    private List<RequestFileRow> lastWritten = List.of();

    public InMemoryGateway() {
    }

    public InMemoryGateway withRows(List<RequestFileRow> rows) {
        this.rowsToReturn = List.copyOf(rows);
        return this;
    }

    public InMemoryGateway withReadFailure(IOException failure) {
        this.readFailure = failure;
        return this;
    }

    public InMemoryGateway withWriteFailure(IOException failure) {
        this.writeFailure = failure;
        return this;
    }

    @Override
    public List<RequestFileRow> readResource() throws IOException {
        readCalls++;
        if (readFailure != null) {
            throw readFailure;
        }
        return rowsToReturn;
    }

    @Override
    public List<RequestFileRow> read(Path path, String format) throws IOException {
        readCalls++;
        if (readFailure != null) {
            throw readFailure;
        }
        return rowsToReturn;
    }

    @Override
    public void write(Path path, String format, List<RequestFileRow> rows) throws IOException {
        writeCalls++;
        if (writeFailure != null) {
            throw writeFailure;
        }
        lastWritten = List.copyOf(rows);
    }

    public int readCalls() {
        return readCalls;
    }

    public int writeCalls() {
        return writeCalls;
    }

    public List<RequestFileRow> lastWritten() {
        return lastWritten;
    }
}