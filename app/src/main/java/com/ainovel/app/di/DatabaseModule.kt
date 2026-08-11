package com.ainovel.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ainovel.app.data.local.AppDatabase
import com.ainovel.app.data.local.dao.NovelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ainovel.db"
        ).addMigrations(Migration1To2()).build()
    }

    @Provides
    @Singleton
    fun provideNovelDao(database: AppDatabase): NovelDao = database.novelDao()
}

private class Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE novels ADD COLUMN source TEXT NOT NULL DEFAULT 'ORIGINAL'"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `imported_texts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`novelId` INTEGER NOT NULL, " +
                "`fullText` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_imported_texts_novelId` ON `imported_texts` (`novelId`)"
        )
        db.execSQL(
            "ALTER TABLE worldviews ADD COLUMN plotSummary TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "ALTER TABLE worldviews ADD COLUMN styleProfile TEXT NOT NULL DEFAULT ''"
        )
    }
}
