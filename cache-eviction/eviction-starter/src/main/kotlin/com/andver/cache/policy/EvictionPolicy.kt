package com.andver.cache.policy

/**
 * Контракт политики вытеснения. Кэш сообщает о доступах, политика только
 * ведёт метаданные и выбирает жертву — сами записи хранит [com.andver.cache.runtime.InMemoryBoundedCache].
 */
interface EvictionPolicy<K : Any> {
  /** Кэш прочитал живой ключ. Политика может обновить recency / частоту. */
  fun onGet(key: K)

  /**
   * Ключ записали в кэш.
   * @param isNew `true`, если ключа раньше не было (первая вставка).
   */
  fun onPut(key: K, isNew: Boolean)

  /** Ключ уже убрали из стора (expire / invalidate / предыдущая eviction). Сметаем метаданные. */
  fun onRemove(key: K)

  /**
   * Кого выкинуть при `size > maxSize`.
   * @return ключ-жертва или `null`, если вытеснять некого.
   */
  fun pickVictim(): K?
}
