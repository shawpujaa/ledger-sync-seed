package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.report.Reports;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Command line entry point.
 *
 *   migrate                  apply db/migration/*.sql
 *   ingest  <corpus.jsonl>   read a corpus into the ledger
 *   report  <out-dir> [totals] write ledger.json, summary.json, reconciliation.json
 */
public final class App {

    private static final Path DB = Path.of("data", "ledger");
    private static final Path MIGRATIONS = Path.of("db", "migration");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: migrate | ingest <corpus.jsonl> | report <out-dir>");
            System.exit(2);
        }
        Files.createDirectories(DB.getParent());

        switch (args[0]) {
            case "migrate" -> {
                try (SqlLedgerStore store = new SqlLedgerStore(DB)) {
                    store.migrate(MIGRATIONS);
                    System.out.println("ledger rows: " + store.count());
                }
            }
            case "ingest" -> {
                if (args.length < 2) throw new IllegalArgumentException("ingest needs a corpus");
                try (SqlLedgerStore store = new SqlLedgerStore(DB)) {
                    store.migrate(MIGRATIONS);
                    var stats = new IngestService(new Parsers(), store)
                            .ingestFile(Path.of(args[1]));
                    System.out.println(stats);
                    System.out.println("ledger rows: " + store.count());
                }
            }
            case "report" -> {
                if (args.length < 2) throw new IllegalArgumentException("report needs a directory");
                Path out = Path.of(args[1]);
                Path totalsPath = Path.of(args.length >= 3
                    ? args[2] : "fixtures/corpus-a-totals.json");
                Files.createDirectories(out);
                try (SqlLedgerStore store = new SqlLedgerStore(DB)) {
                    var ledger = store.all();
                    var totals = Json.parseObject(Files.readString(totalsPath));
                    var balances = balancesFrom(totals);
                    Files.writeString(out.resolve("ledger.json"),
                            Json.writePretty(Reports.ledgerDocument(ledger)));
                    Files.writeString(out.resolve("summary.json"),
                            Json.writePretty(Reports.summary(ledger)));
                    Files.writeString(out.resolve("reconciliation.json"),
                        Json.writePretty(Reports.reconciliation(
                            ledger, balances.opening(), balances.closing())));
                    System.out.println("wrote 3 files to " + out);
                }
            }
            default -> {
                System.err.println("unknown command: " + args[0]);
                System.exit(2);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Balances balancesFrom(Map<String, Object> totals) {
        Map<String, BigDecimal> opening = new LinkedHashMap<>();
        Map<String, BigDecimal> closing = new LinkedHashMap<>();
        Map<String, Object> accounts = (Map<String, Object>) totals.get("accounts");
        for (Map.Entry<String, Object> entry : accounts.entrySet()) {
            Map<String, Object> account = (Map<String, Object>) entry.getValue();
            opening.put(entry.getKey(), new BigDecimal((String) account.get("opening_balance")));
            closing.put(entry.getKey(), new BigDecimal((String) account.get("closing_balance")));
        }
        return new Balances(opening, closing);
    }

    private record Balances(Map<String, BigDecimal> opening, Map<String, BigDecimal> closing) {}
}
