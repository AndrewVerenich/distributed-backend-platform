package com.andver.geomatching.store

import com.andver.geomatching.model.ProviderStatus
import com.andver.geomatching.model.RequestStatus
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.PostgresDialect

@WritingConverter
class ProviderStatusWriteConverter : Converter<ProviderStatus, String> {
  override fun convert(source: ProviderStatus): String = source.db()
}

@ReadingConverter
class ProviderStatusReadConverter : Converter<String, ProviderStatus> {
  override fun convert(source: String): ProviderStatus = ProviderStatus.fromDb(source)
}

@WritingConverter
class RequestStatusWriteConverter : Converter<RequestStatus, String> {
  override fun convert(source: RequestStatus): String = source.db()
}

@ReadingConverter
class RequestStatusReadConverter : Converter<String, RequestStatus> {
  override fun convert(source: String): RequestStatus = RequestStatus.fromDb(source)
}

fun geoMatchingR2dbcConversions(): R2dbcCustomConversions =
  R2dbcCustomConversions.of(
    PostgresDialect.INSTANCE,
    listOf(
      ProviderStatusWriteConverter(),
      ProviderStatusReadConverter(),
      RequestStatusWriteConverter(),
      RequestStatusReadConverter(),
    ),
  )
