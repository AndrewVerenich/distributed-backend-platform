package com.andver.banking.command.handler

import com.andver.banking.command.model.AccountCommand
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.stereotype.Component

interface CommandSerializer {
  fun serialize(cmd: AccountCommand): String
}

@Component
class DefaultCommandSerializer : CommandSerializer {
  private val mapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

  override fun serialize(cmd: AccountCommand): String = mapper.writeValueAsString(cmd)
}