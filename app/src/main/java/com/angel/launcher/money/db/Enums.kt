package com.angel.launcher.money.db

/** DEBIT/CREDIT rather than the parser's OUT/IN — this is the persisted, user-facing name. */
enum class TxnDirection { DEBIT, CREDIT }

enum class AccountType { BANK, CREDIT_CARD }

enum class TxnMode { UPI, CARD, NEFT, IMPS, ATM, OTHER }
