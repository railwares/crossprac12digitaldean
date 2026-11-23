package ua.edu.digitaldean.app;

import java.io.PrintStream;
import java.util.function.Supplier;
import java.util.logging.Logger;
import ua.edu.digitaldean.service.DiagnosticLog;
import ua.edu.digitaldean.service.FileRequestGateway;
import ua.edu.digitaldean.service.RequestExchangeService;
import ua.edu.digitaldean.service.RequestProcessor;
import ua.edu.digitaldean.service.StandardFeePolicy;

public final class FileExchangeApp {
    private FileExchangeApp() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err, DiagnosticLog::create);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err,
                          Supplier<Logger> loggerFactory) {
        RequestExchangeService service = new RequestExchangeService(
                new FileRequestGateway(),
                new RequestProcessor(new StandardFeePolicy()));
        ConsoleApplication application = new ConsoleApplication(service, new SummaryPrinter());
        return application.run(args, out, err, loggerFactory);
    }
}