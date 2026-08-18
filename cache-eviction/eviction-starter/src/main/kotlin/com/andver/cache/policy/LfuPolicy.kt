package com.andver.cache.policy

/**
 * LFU — Least Frequently Used.
 *
 * Жертва — ключ с **минимальным числом обращений**. При равной частоте
 * выигрывает более старый по [tick] (лёгкий aging / tie-break по recency):
 * два ключа с freq=1 не выбираются случайно — уходит тот, к которому
 * дольше не обращались.
 *
 * Полного затухания счётчиков нет: вчерашний хит с большой частотой
 * может жить дольше, чем свежий умеренно популярный ключ.
 */
class LfuPolicy<K : Any> : EvictionPolicy<K> {
  /** Сколько раз ключ трогали (get + put). */
  private val frequencies = HashMap<K, Int>()

  /** Логическое время последнего доступа; больше = свежее. */
  private val lastAccessTick = HashMap<K, Long>()

  /** Монотонный счётчик событий доступа, не wall-clock. */
  private var tick: Long = 0

  /**
   * Hit увеличивает частоту и обновляет tick.
   * [Map.computeIfPresent] — если ключа уже нет в метаданных, счётчик не создаём.
   */
  override fun onGet(key: K) {
    tick++
    frequencies.computeIfPresent(key) { _, v -> v + 1 }
    lastAccessTick[key] = tick
  }

  /**
   * Первая вставка: частота = 1.
   * Повторный put: как обращение, частота +1 (перезапись тоже «использование»).
   */
  override fun onPut(key: K, isNew: Boolean) {
    tick++
    if (isNew) {
      frequencies[key] = 1
    } else {
      frequencies.computeIfPresent(key) { _, v -> v + 1 }
    }
    lastAccessTick[key] = tick
  }

  /** Полностью забываем ключ, чтобы он не участвовал в выборе жертвы. */
  override fun onRemove(key: K) {
    frequencies.remove(key)
    lastAccessTick.remove(key)
  }

  /**
   * Линейный скан: ищем min(freq), при равенстве — min(lastAccessTick).
   * Для учебного кэша O(n) приемлемо; в проде обычно heap / frequency buckets.
   */
  override fun pickVictim(): K? {
    var victim: K? = null
    var minFreq = Int.MAX_VALUE
    var oldestTick = Long.MAX_VALUE
    for ((key, freq) in frequencies) {
      val lastTick = lastAccessTick[key] ?: Long.MAX_VALUE
      if (freq < minFreq || (freq == minFreq && lastTick < oldestTick)) {
        minFreq = freq
        oldestTick = lastTick
        victim = key
      }
    }
    return victim
  }
}
