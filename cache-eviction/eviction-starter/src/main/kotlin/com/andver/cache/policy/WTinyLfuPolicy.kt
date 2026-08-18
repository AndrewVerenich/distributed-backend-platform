package com.andver.cache.policy

import com.andver.cache.sketch.CountMinSketch
import java.util.LinkedHashSet

/**
 * W-TinyLFU — Window Tiny Least Frequently Used (как в Caffeine).
 *
 * Новый ключ не занимает место «за просто так»: сначала живёт в коротком
 * LRU-окне, а в основной кэш ([probationSegment] / [protectedSegment])
 * попадает только если Count-Min Sketch говорит, что он не реже текущего жильца.
 * One-hit ключи (scan) в protected не пускают — это защита от cache pollution.
 */
class WTinyLfuPolicy<K : Any>(
  expectedInsertions: Int = 100_000,
) : EvictionPolicy<K> {
  /** Оценка частоты *всех* ключей, даже тех, кого в кэше нет. */
  private val sketch = CountMinSketch(expectedInsertions = expectedInsertions, falsePositiveRate = 0.01)

  /** Admission window: новички. LRU, голова — кандидат на выселение из окна. */
  private val recencyWindow = LinkedHashSet<K>()

  /** Горячий сегмент: повторный get из probation повышает ключ сюда. */
  private val protectedSegment = LinkedHashSet<K>()

  /** Основной кэш «на испытании»: ключ принят, но ещё не доказал повторные чтения. */
  private val probationSegment = LinkedHashSet<K>()

  /**
   * Каждое чтение кормит sketch (частота нужна для admission).
   * Дальше — промоушен по сегментам:
   * - protected: остаётся там, но move-to-end (LRU внутри сегмента);
   * - probation → protected: ключ доказал повторный доступ;
   * - window: омолаживаем внутри окна, в основной кэш не поднимаем (это делает [admitted]).
   */
  override fun onGet(key: K) {
    sketch.increment(key)
    when {
      protectedSegment.remove(key) -> protectedSegment.add(key)
      probationSegment.remove(key) -> protectedSegment.add(key)
      recencyWindow.remove(key) -> recencyWindow.add(key)
    }
  }

  /**
   * Новый ключ всегда стартует в window — пусть сначала накопит recency.
   * Повторный put вынимаем из любого сегмента и кладём обратно в window
   * (перезапись = «снова новичок» по позиции, частота в sketch уже учтена).
   */
  override fun onPut(key: K, isNew: Boolean) {
    sketch.increment(key)
    if (!isNew) {
      recencyWindow.remove(key)
      protectedSegment.remove(key)
      probationSegment.remove(key)
      recencyWindow.add(key)
      return
    }
    recencyWindow.add(key)
  }

  /** Убираем ключ из всех трёх сегментов. Sketch не чистим — это эскиз потока, не состав кэша. */
  override fun onRemove(key: K) {
    recencyWindow.remove(key)
    protectedSegment.remove(key)
    probationSegment.remove(key)
  }

  /**
   * Порядок жертв: сначала window (самый дешёвый / одноразовый трафик),
   * потом probation, в крайнем случае protected.
   * Голова LinkedHashSet в каждом сегменте — LRU этого сегмента.
   */
  override fun pickVictim(): K? {
    val candidateFromWindow = recencyWindow.firstOrNull()
    if (candidateFromWindow != null) {
      return candidateFromWindow
    }
    val probationVictim = probationSegment.firstOrNull()
    if (probationVictim != null) {
      return probationVictim
    }
    return protectedSegment.firstOrNull()
  }

  /**
   * Фильтр admission: можно ли новичку занять место [victim].
   *
   * Сравниваем оценки частоты в sketch. Если новичок не реже жильца —
   * пускаем его в probation и выселяем victim. Иначе новичка выбрасываем
   * из window, жилец остаётся (scan не загрязняет основной кэш).
   *
   * Вызывается из [com.andver.cache.runtime.InMemoryBoundedCache], не из [pickVictim].
   */
  fun admitted(newKey: K, victim: K?): Boolean {
    if (victim == null) return true
    val admitted = sketch.estimate(newKey) >= sketch.estimate(victim)
    if (admitted) {
      recencyWindow.remove(newKey)
      probationSegment.add(newKey)
      probationSegment.remove(victim)
    } else {
      recencyWindow.remove(newKey)
    }
    return admitted
  }
}
