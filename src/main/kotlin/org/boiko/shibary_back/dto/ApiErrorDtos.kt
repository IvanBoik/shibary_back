package org.boiko.shibary_back.dto

data class ApiErrorResponse(val error: ApiErrorDto)

data class ApiErrorDto(
  val code: String,
  val message: String,
)
