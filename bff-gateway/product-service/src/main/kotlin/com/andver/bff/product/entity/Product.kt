package com.andver.bff.product.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("product")
data class Product(
  @Id val id: Long? = null,
  val name: String,
  val description: String?,
  val price: BigDecimal,
  val category: String,
  val images: String?,
  val rating: BigDecimal,
  @Column("review_count") val reviewCount: Int,
  @Column("sales_count") val salesCount: Int,
  @Column("created_at") val createdAt: Instant,
  @Column("updated_at") val updatedAt: Instant,
  @Column("created_by") val createdBy: String?,
  @Column("is_active") val isActive: Boolean,
)
