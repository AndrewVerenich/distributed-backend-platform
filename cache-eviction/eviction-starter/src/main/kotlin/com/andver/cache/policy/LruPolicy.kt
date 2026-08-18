package com.andver.cache.policy

import java.util.LinkedHashSet

/**
 * LRU — Least Recently Used.
 *
 * Жертва — ключ, к которому **дольше всего не обращались**.
 * `get` и `put` переносят ключ в конец списка (самый свежий);
 * голова списка — самый холодный, его и вытесняем.
 *
 * LinkedHashSet даёт O(1) remove + add в конец (access-order без ручного двусвязного списка).
 */
class LruPolicy<K : Any> : EvictionPolicy<K> {
  /** Итерация с начала = от LRU к MRU. */
  private val order = LinkedHashSet<K>()

  /**
   * Hit: ключ «омолаживается». Удаляем из текущей позиции и ставим в конец.
   * Если ключа нет (гонка / уже вытеснен) — ничего не делаем.
   */
  override fun onGet(key: K) {
    if (order.remove(key)) {
      order.add(key)
    }
  }

  /**
   * Новая запись сразу становится MRU (хвост).
   * Обновление существующего — тоже move-to-end: put считается обращением.
   */
  override fun onPut(key: K, isNew: Boolean) {
    if (!isNew) {
      order.remove(key)
    }
    order.add(key)
  }

  /** Ключ ушёл из стора — убираем из порядка, иначе [pickVictim] вернёт призрак. */
  override fun onRemove(key: K) {
    order.remove(key)
  }

  /** Голова LinkedHashSet — наименее недавно использованный ключ. */
  override fun pickVictim(): K? = order.firstOrNull()
}
