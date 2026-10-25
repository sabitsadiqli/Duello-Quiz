package com.sabit.duelloquiz.di

import com.sabit.duelloquiz.core.data.licenses.BundledLicenseFiles
import com.sabit.duelloquiz.licenses.AndroidBundledLicenseFiles
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LicensesModule {
    @Binds
    abstract fun bindBundledLicenseFiles(impl: AndroidBundledLicenseFiles): BundledLicenseFiles
}
