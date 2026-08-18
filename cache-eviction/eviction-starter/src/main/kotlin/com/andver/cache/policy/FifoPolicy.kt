package com.andver.cache.policy

import java.util.ArrayDeque
import java.util.HashSet

/**
 * FIFO — First In, First Out.
 *
 * Жертва — самый старый по **времени вставки**. Чтения ключ не омолаживают:
 * горячий ключ, попавший в кэш давно, уйдёт раньше свежего холодного.
 */
class FifoPolicy<K : Any> : EvictionPolicy<K> {
  /** Порядок вставки: голова — самый старый, хвост — самый новый. */
  private val queue = ArrayDeque<K>()

  /**
   * Живые ключи. Нужен, потому что [onRemove] не вычищает очередь (O(n)):
   * [pickVictim] просто пропускает «призраков», которых уже нет в [known].
   */
  private val known = HashSet<K>()

  /** Get recency не меняет: FIFO смотрит только на момент вставки. */
  override fun onGet(key: K) = Unit

  /**
   * В очередь попадает только **первая** вставка. Повторный put того же ключа
   * (обновление значения) позицию в FIFO не двигает.
   */
  override fun onPut(key: K, isNew: Boolean) {
    if (isNew && known.add(key)) {
      queue.addLast(key)
    }
  }

  /**
   * Помечаем ключ мёртвым. Саму очередь не трогаем — удаление из середины
   * ArrayDeque дорого, отложенная чистка в [pickVictim].
   */
  override fun onRemove(key: K) {
    known.remove(key)
  }

  /**
   * Снимаем голову очереди, пока не найдём ключ, который ещё в [known].
   * Пропущенные элементы — уже удалённые/истёкшие, их просто выбрасываем.
   */
  override fun pickVictim(): K? {
    while (queue.isNotEmpty()) {
      val candidate = queue.removeFirst()
      if (known.contains(candidate)) {
        return candidate
      }
    }
    return null
  }
}
