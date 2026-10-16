package com.almakhedhi.rxreader.core.database.di

import android.content.Context
import androidx.room.Room
import com.almakhedhi.rxreader.core.database.MIGRATION_1_2
import com.almakhedhi.rxreader.core.database.RxDatabase
import com.almakhedhi.rxreader.core.database.dao.DrugAliasDao
import com.almakhedhi.rxreader.core.database.dao.DrugDao
import com.almakhedhi.rxreader.core.database.dao.DrugImageDao
import com.almakhedhi.rxreader.core.database.dao.EnrichCacheDao
import com.almakhedhi.rxreader.core.database.dao.ImportJobDao
import com.almakhedhi.rxreader.core.database.dao.PrescriptionDao
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
    fun provideDatabase(@ApplicationContext context: Context): RxDatabase =
        Room.databaseBuilder(context, RxDatabase::class.java, "rxreader.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideDrugDao(db: RxDatabase): DrugDao = db.drugDao()

    @Provides
    fun provideAliasDao(db: RxDatabase): DrugAliasDao = db.aliasDao()

    @Provides
    fun provideImageDao(db: RxDatabase): DrugImageDao = db.imageDao()

    @Provides
    fun providePrescriptionDao(db: RxDatabase): PrescriptionDao =
        db.prescriptionDao()

    @Provides
    fun provideImportJobDao(db: RxDatabase): ImportJobDao = db.importJobDao()

    @Provides
    fun provideEnrichCacheDao(db: RxDatabase): EnrichCacheDao = db.enrichCacheDao()
}
