package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads a corpus of raw messages and writes a ledger of real transactions.
 *
 * One bank message may be a duplicate of another, and one real transaction often
 * appears in both an SMS and an email. The service therefore groups all evidence
 * for the same transaction before saving it.
 */
public final class IngestService {

    private final Parsers parsers;
    private final LedgerStore store;

    public IngestService(Parsers parsers, LedgerStore store) {
        this.parsers = parsers;
        this.store = store;
    }

    public Stats ingestFile(Path corpus) throws IOException {
        List<RawMessage> messages = readCorpus(corpus);
        Map<TransactionKey, List<ParsedTxn>> byKey = new LinkedHashMap<>();
        int skipped = 0;

        for (RawMessage m : messages) {
            Optional<ParsedTxn> parsed = parsers.parse(m);
            if (parsed.isEmpty()) {
                skipped++;
                continue;
            }
            TransactionKey key = new TransactionKey(
                    parsed.get().accountLast4(),
                    parsed.get().occurredAt(),
                    parsed.get().direction(),
                    parsed.get().amount(),
                    normalizeMerchant(parsed.get().merchant()));
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(parsed.get());
        }

        List<List<ParsedTxn>> groups = new ArrayList<>(byKey.values());
        List<NormalizedTxn> written = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            written.add(toTransaction(groups.get(i), isTransferLeg(i, groups)));
        }

        for (NormalizedTxn txn : written) {
            store.save(txn);
        }

        return new Stats(messages.size(), written.size(), skipped);
    }

    public static List<RawMessage> readCorpus(Path corpus) throws IOException {
        List<RawMessage> out = new ArrayList<>();
        try (Stream<String> lines = Files.lines(corpus)) {
            for (String line : (Iterable<String>) lines.filter(s -> !s.isBlank())::iterator) {
                Map<String, Object> o = Json.parseObject(line);
                out.add(new RawMessage(
                        (String) o.get("message_id"),
                        (String) o.get("channel"),
                        (String) o.get("sender"),
                        OffsetDateTime.parse((String) o.get("received_at")),
                        (String) o.get("device_id"),
                        (String) o.get("body")));
            }
        }
        return out;
    }

    private NormalizedTxn toTransaction(List<ParsedTxn> group, boolean transfer) {
        ParsedTxn first = group.getFirst();
        List<String> ids = group.stream()
                .map(ParsedTxn::sourceMessageId)
                .distinct()
                .sorted()
                .toList();

        return new NormalizedTxn(
                first.accountLast4(),
                first.occurredAt(),
                first.direction(),
                first.amount(),
                categoryFor(first, transfer),
                first.merchant(),
                ids);
    }

    private static boolean isTransferLeg(int index, List<List<ParsedTxn>> groups) {
        ParsedTxn candidate = groups.get(index).getFirst();
        if (!isTransferLike(normalizeMerchant(candidate.merchant()).toUpperCase(Locale.ROOT))) {
            return false;
        }

        for (int otherIndex = 0; otherIndex < groups.size(); otherIndex++) {
            if (otherIndex == index) continue;
            ParsedTxn other = groups.get(otherIndex).getFirst();
            if (!other.accountLast4().equals(candidate.accountLast4())
                    && other.direction() != candidate.direction()
                    && other.amount().compareTo(candidate.amount()) == 0
                    && normalizeMerchant(other.merchant()).equalsIgnoreCase(
                            normalizeMerchant(candidate.merchant()))) {
                return true;
            }
        }
        return false;
    }

    private static Category categoryFor(ParsedTxn p, boolean transfer) {
        String merchant = normalizeMerchant(p.merchant());
        String up = merchant.toUpperCase(Locale.ROOT);

        if (transfer) {
            return Category.TRANSFER;
        }

        if (p.direction() == Direction.DEBIT
                && p.amount().compareTo(new BigDecimal("100.00")) <= 0
                && up.startsWith("UPI")) {
            return Category.MICRO;
        }

        return p.direction() == Direction.DEBIT ? Category.SPEND : Category.INCOME;
    }

    private static boolean isTransferLike(String merchant) {
        return merchant.contains("IMPS/P2A/")
                || merchant.contains("NEFT INWARD")
                || merchant.contains("P2P/REFUND");
    }

    private static String normalizeMerchant(String merchant) {
        return merchant == null ? "" : merchant.trim();
    }

    public record Stats(int messagesRead, int transactionsWritten, int messagesSkipped) {}

    private record TransactionKey(
            String accountLast4,
            OffsetDateTime occurredAt,
            Direction direction,
            BigDecimal amount,
            String merchant) {}
}
