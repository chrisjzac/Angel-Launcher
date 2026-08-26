package com.angel.launcher.money.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDirection(value: TxnDirection): String = value.name

    @TypeConverter
    fun toDirection(value: String): TxnDirection = TxnDirection.valueOf(value)

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromMode(value: TxnMode): String = value.name

    @TypeConverter
    fun toMode(value: String): TxnMode = TxnMode.valueOf(value)
}
