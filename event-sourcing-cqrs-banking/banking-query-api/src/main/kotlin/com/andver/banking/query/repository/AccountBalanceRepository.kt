package com.andver.banking.query.repository

import com.andver.banking.domain.entity.AccountBalance
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface AccountBalanceRepository : ReactiveCrudRepository<AccountBalance, Long>