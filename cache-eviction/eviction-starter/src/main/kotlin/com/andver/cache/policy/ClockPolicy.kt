package com.andver.cache.policy

/**
 * CLOCK / Second Chance — приближение LRU из OS page replacement.
 *
 * На каждый get не двигаем ноду в списке (дорого), а только ставим бит
 * «к этой странице обращались». Стрелка часов ([hand]) идёт по кольцу:
 * бит = 1 → сбрасываем и даём **второй шанс**; бит = 0 → жертва.
 */
class ClockPolicy<K : Any> : EvictionPolicy<K> {
  /** Неупорядоченное кольцо ключей. Индекс в списке — позиция «страницы». */
  private val ring = ArrayList<K>()

  /** key → индекс в [ring], чтобы [onRemove] был O(1). */
  private val index = HashMap<K, Int>()

  /** Reference bit: true = недавно трогали, false = можно выселять. */
  private val referenced = HashMap<K, Boolean>()

  /** Текущая позиция стрелки. После выселения сдвигается дальше. */
  private var hand = 0

  /**
   * Hit не двигает ключ по кольцу — только поднимает reference bit.
   * Именно поэтому CLOCK дешевле LRU на hot path.
   */
  override fun onGet(key: K) {
    if (index.containsKey(key)) {
      referenced[key] = true
    }
  }

  /**
   * Новый ключ дописывается в конец кольца с bit=true (только что использован).
   * Обновление существующего ключа бит тоже поднимает, позицию не меняет.
   */
  override fun onPut(key: K, isNew: Boolean) {
    if (isNew) {
      index[key] = ring.size
      ring.add(key)
    }
    referenced[key] = true
  }

  /**
   * Удаляем из кольца за O(1): дырку заполняем последним элементом
   * (swap-remove), чиним его индекс. Стрелку держим в границах списка.
   */
  override fun onRemove(key: K) {
    val i = index.remove(key) ?: return
    referenced.remove(key)
    val lastIdx = ring.lastIndex
    if (i != lastIdx) {
      val lastKey = ring[lastIdx]
      ring[i] = lastKey
      index[lastKey] = i
    }
    ring.removeAt(lastIdx)
    if (ring.isEmpty()) {
      hand = 0
    } else if (hand >= ring.size) {
      hand = 0
    }
  }

  /**
   * Обход кольца (не больше двух полных оборотов):
   * 1. referenced=true → second chance: бит в false, стрелка дальше;
   * 2. referenced=false → этот ключ не трогали с прошлого оборота, выселяем.
   *
   * Fallback на первый элемент — защита от теоретического бесконечного цикла,
   * если все биты внезапно true.
   */
  override fun pickVictim(): K? {
    if (ring.isEmpty()) return null
    var steps = 0
    while (steps < ring.size * 2) {
      if (hand >= ring.size) hand = 0
      val key = ring[hand]
      if (referenced[key] == true) {
        referenced[key] = false
        hand++
      } else {
        hand++
        return key
      }
      steps++
    }
    return ring.firstOrNull()
  }
}
