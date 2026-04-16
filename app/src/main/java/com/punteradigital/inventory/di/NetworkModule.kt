package com.punteradigital.inventory.di

import android.content.Context
import com.punteradigital.inventory.data.remote.GoogleSheetsService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("GoogleRetrofit")
    fun provideGoogleRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Note: PrintRetrofit is no longer provided as a Singleton here.
    // PrintRepository dynamically creates its own Retrofit instance
    // using the current IP/port from PrinterPreferences, enabling
    // runtime reconfiguration without app restart.

    @Provides
    @Singleton
    fun provideGoogleSheetsService(@Named("GoogleRetrofit") retrofit: Retrofit): GoogleSheetsService {
        return retrofit.create(GoogleSheetsService::class.java)
    }
}

