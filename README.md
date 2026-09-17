Run it:

```bash
./verify.sh                      # compile + run the pipeline, no network needed
./gradlew test                   # the test suite (needs network once, for JUnit)
./gradlew run --args="migrate"
./gradlew run --args="ingest fixtures/corpus-a.jsonl"
./gradlew run --args="report submission/"
```
---

## Decisions
1. I used plain Java and did not add a framework. The project reads a file and writes reports, so a web framework was not required.
2. There was no transaction ID. I used account_last4, occurred_at, direction, amount, and merchant combination to identify unique transactions.
3. After adding the email parser and all ICICI SMS formats, the two bank accounts contained 237 transactions. The remaining transactions came from the HDFC credit card ending in 3310.
4. I separated transactions into MICRO and TRANSFER during ingest and stored the category in the ledger.
5. I marked a transaction as TRANSFER only when the other account had an opposite debit or credit with the same amount and merchant. I did not treat every IMPS/P2A/, NEFT INWARD, or P2P/REFUND message as a transfer.
6. I changed the amount parser to accept integer values and values with upto two decimal places. This fixed the production bug where the transaction amount was skipped and the available balance was used instead.
7. One transaction for account 4821 reports a debit of 75, but the available balance changes by 7,575. I report the unexplained 7,500 difference separately in reconciliation. This is why the 4821 summary does not match the fixture.
8. I kept card suffix 3310 separate because it is a card number, not proof that it belongs to bank account 4821 or 9075.

---

## What The Data Showed
- Transfer-like merchants include IMPS/P2A/, NEFT INWARD, and P2P/REFUND, but a matching opposite account transaction is also required.
- Not every SMS or email is a transaction. Some are delivery messages, OTPs, warnings, or balance-only messages.
- The only two bank accounts end in 4821 and 9075. Another source is the HDFC card ending in 3310.
- The corpus has SMS and email channels. ICICI has two SMS formats. HDFC has two bank-account SMS formats and one card format. Each bank's emails use one format.
- The corpus has 522 raw messages. The pipeline writes 256 transactions, while the fixture expects 257. Account 9075 reconciles; account 4821 has the separate 7,500 discrepancy.

## The document store
 Is not implemented.

## AI Disclosure
I used GitHub Copilot in VS Code for code search, debugging, small edits, and focused checks. I checked the results against the corpus myself.

One wrong suggestion was to find or invent a missing 7,500 transaction so the numbers would match. The corpus has no such message, so I chose to report the difference instead. Another suggestion was to map card `3310` to account `4821`; I rejected that because the data does not prove the mapping. 

One more mistake happened after the parsers and ingest service were fixed. I ran verify.sh and got 145 transactions for **4821 and 92 for **9075. The AI kept on suggesting that transfer calculations were wrong. After checking, the real issue was amounts calculation was creating two different entries for same transaction where in one transaction it was an integer value hence taking available balance as debited amount.

## Unfinished

- Document store implementation
- Backfill
- Consistency checker
- Docker Compose setup for a document store

The completed work is parsing, deduplication, incident reproducing-fixing, category-separated summaries, and reconciliation reporting.
