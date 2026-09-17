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

1. I used the existing plain Java format and did not add any framework, since this project reads a file and writes reports, so a web framework was not required.
2. Since there was no transaction id to uniquely identify a transaction I used combination of account_last4, occurred_at, direction, amount and merchant to uniquely identify any transaction. If two transactions strictly had all of these data same they were considered as same.
3. Even after adding email parser and adding all icici sms patterns, the two accounts listed 237 transactions only. The rest came from hdfc credit card ending with **3310, which I realsied after debugging.
4. Bifarcated transactions as MICRO/ TRANSFERS in ingest service.
5. I only marked a transaction as TRANSFER when it was debited from one account and credited in the other account at the same time. Not all IMPS/P2A/, NEFT INWARD, P2P/REFUND merchant type is considered as transfer. Rest of them were considered normal spend, income.
6. Changed the old amount parser to accept integer values and values with one decimal place as well. This was the reason for production bug. I only accounted for till 2 decimal places since the corpus had till that only.
7. One transaction for account **4821 is not correct. It reports a debit of 75 but change in the available balance is of Rs.7575. Added that separately in reconcilation. That is why spend amount in summary report for **4821 account does not match. 

---

## What The Data Showed
- TRANSFERS have IMPS/P2A/, NEFT INWARD, P2P/REFUND in the merchant name.
- Not all message/email is of any transaction some are random message/emails as well.
- There are only two accounts associated ending with **4821 and **9075. One more source is listed which is an HDFC card ending with **3310.
- There are only two channels email and sms. Email had only one format in which emails are sent. ICICI bank had two sms formats. HDFC bank sms's had two sms formats.
- 

## The document store

The ledger is moving off SQL onto a document store. **DynamoDB preferred,
MongoDB fine** — your choice, and say why. It must run from your
`docker compose up`.

`DocumentStore` declares the only three queries this service makes:

1. one account's transactions for one month, newest first
2. running totals per category for an account
3. given a message id, which transaction did it produce

Design your documents so the engine serves these directly. We are not going to
tell you what a document should look like — that decision is the exercise.

For each of the three, **report how many items the engine examined versus how
many it returned, at 100,000 transactions.** DynamoDB gives you `ScannedCount`
and `Count`; MongoDB gives you `totalDocsExamined` and `nReturned`. Put the six
numbers in your README.

Then:

- **`Backfill`** moves what is already in SQL across. Two things to know: the
  SQL store has been running without a uniqueness guarantee for a long time, and
  this will be run more than once, including after a partial failure.
- **`ConsistencyChecker`** proves the two stores agree and names precisely where
  they do not. We will run yours against a document store we have deliberately
  altered. It has to find what we changed. A checker that compares row counts
  will not.

---

## Rules

- `model/NormalizedTxn.java`, `model/Category.java` and
  `src/test/.../NormalizedTxnContractTest.java` are **frozen**. Do not edit
  them. Everything behind them is yours.
- Java. Any framework, or none — say why in your decision log.
- Real commit history. Not one squashed commit.
- If something in here is wrong or unclear, **email us**. Guessing when you
  could have asked is a worse signal than asking.

`talent.acquisition@simplifymoney.in`
