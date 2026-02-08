package com.andver.banking.query.repository

import com.andver.banking.query.model.AccountBalance
import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface AccountBalanceRepository : ReactiveCrudRepository<AccountBalance, Long>