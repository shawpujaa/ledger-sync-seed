package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bank transaction alert emails.
 */
public final class EmailParser implements MessageParser {

    @Override
    public boolean supports(RawMessage m) {
        return "email".equals(m.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        Matcher match = ALERT.matcher(m.body());
        if (!match.find()) return Optional.empty();

        OffsetDateTime occurredAt;
        try {
            occurredAt = OffsetDateTime.parse(match.group("date").trim(), EMAIL_DATE)
                .withOffsetSameInstant(Dates.IST);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }

        BigDecimal amount = new BigDecimal(match.group("amount").replace(",", ""))
            .setScale(2);
        Direction direction = "debited".equalsIgnoreCase(match.group("dir"))
            ? Direction.DEBIT : Direction.CREDIT;
        return Optional.of(new ParsedTxn(
            match.group("acct"), occurredAt, direction, amount,
            match.group("merchant").trim(), null, m.messageId()));
    }

    private static final Pattern ALERT = Pattern.compile(
            "Date:\\s*(?<date>[^\\n\\r]+).*?"
                + "account ending (?<acct>\\d{4}) has been "
                + "(?<dir>debited|credited) with "
                + "(?:(?:Rs\\.?|INR)\\s*)?(?<amount>[0-9,]+(?:\\.[0-9]{1,2})?).*?"
                + "Merchant / Remarks:\\s*(?<merchant>[^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final DateTimeFormatter EMAIL_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss xx", Locale.ENGLISH);
}
