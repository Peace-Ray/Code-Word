package com.peaceray.codeword.utils.histogram

import java.lang.IllegalStateException

/**
 * An IntHistogram is a mapping of Ints to Ints with a default value (usually zero) and a range
 * within which non-zero values may appear. Unlike Maps, querying absent keys within the range
 * will provide the default value; querying outside of the range will produce 0. If the default
 * value is set for a key, it is equivalent to deleting that entry. Accordingly, state queries like [size],
 * [containsKey], [entries], etc. have more complex operation than a standard map, based on the
 * presence and position of explicit range boundaries.
 *
 * Non-zero default values require that explicit range boundaries are set for both minimum and maximum keys.
 * When a boundary is not set, a soft boundary is inferred from the min (max resp.) key inserted into
 * the map, but this does not limit the insertion of new entries beyond that soft boundary.
 *
 * @param min The explicit minimum key. Typical usage may set this to 0.
 * @param max The explicit maximum key. Typical usage may set this to the highest possible key,
 * even if that key ends up with the default occurrence count.
 * @param default The default value for unset keys. Typically 0. Cannot be changed after instantiation
 * unless all data is cleared and must be zero unless explicit [min] and [max] values are provided.
 */
class IntHistogram(min: Int? = null, max: Int? = null, default: Int = 0): MutableMap<Int, Int> {
    init {
        if (min != null && max != null && min > max) {
            throw IllegalArgumentException("Can't set range min greater than range max")
        }

        if (default != 0 && (min == null || max == null)) {
            throw IllegalArgumentException("Non-zero default requires explicit range boundaries")
        }
    }

    private val _map = mutableMapOf<Int, Int>()
    private var _mapMinKey: Int? = null
    private var _mapMaxKey: Int? = null

    private var _mapTotal = 0

    var default = default
        private set
    var min = min
        private set
    var max = max
        private set
    val total get() = if (default == 0) _mapTotal else _mapTotal + default * (size - _map.size)

    //region Constructors and Factory Methods
    //---------------------------------------------------------------------------------------------
    companion object {

        //region String Manipulation
        //-----------------------------------------------------------------------------------------
        fun fromString(string: String): IntHistogram {
            return IntHistogram().resetFromString(string)
        }

        fun addInString(string: String, key: Int, value: Int): String {
            val min = readValueInString(string, "min")
            val max = readValueInString(string, "max")
            if (!nullOrLTE(min, key, max)) {
                throw IllegalArgumentException("Insertion keys must fit within non-null min/max boundaries")
            }

            val default = readValueInString(string, "default")!!
            val update = (readValueInString(string, key) ?: default) + value
            return if (update == default) {
                removeValueInString(string, key)
            } else {
                setValueInString(string, key, update)
            }
        }

        fun setInString(string: String, key: Int, value: Int): String {
            val min = readValueInString(string, "min")
            val max = readValueInString(string, "max")
            if (!nullOrLTE(min, key, max)) {
                throw IllegalArgumentException("Insertion keys must fit within non-null min/max boundaries")
            }

            val default = readValueInString(string, "default")!!
            return if (value == default) {
                removeValueInString(string, key)
            } else {
                setValueInString(string, key, value)
            }
        }

        private fun readValueInString(string: String, key: Int) =
            readValueInString(string, "$key")

        private fun readValueInString(string: String, key: String): Int? {
            val keyStr = ">$key="
            val keyIndex = string.indexOf(keyStr)
            if (keyIndex < 0) return null

            val valueIndex = keyIndex + keyStr.length
            val valueTo = string.indexOf(",", startIndex = valueIndex)
            return when {
                valueTo < 0 -> string.substring(valueIndex)     // the last entry
                else -> string.substring(valueIndex, valueTo)   // not the last
            }.toInt()
        }

        private fun setValueInString(string: String, key: Int, value: Int) =
            setValueInString(string, "$key", "$value")

        private fun setValueInString(string: String, key: String, value: String): String {
            val keyStr = ">$key="
            val keyIndex = string.indexOf(keyStr)
            return if (keyIndex < 0) {
                if (string.isEmpty()) {
                    ">$key=$value"
                } else {
                    "$string,>$key=$value"
                }
            } else {
                val valueIndex = keyIndex + keyStr.length
                val valueTo = string.indexOf(",", startIndex = valueIndex)
                if (valueTo < 0) {
                    "${string.substring(0, valueIndex)}$value"
                } else {
                    "${string.substring(0, valueIndex)}$value${string.substring(valueTo)}"
                }
            }
        }

        private fun removeValueInString(string: String, key: Int) =
            removeValueInString(string, "$key")

        private fun removeValueInString(string: String, key: String): String {
            val keyStr = ">$key="
            val keyIndex = string.indexOf(keyStr)
            return if (keyIndex < 0) {
                string
            } else {
                val valueIndex = keyIndex + keyStr.length
                val valueTo = string.indexOf(",", startIndex = valueIndex)
                when {
                    valueTo < 0 && keyIndex == 0 -> ""                       // the only entry
                    valueTo < 0 -> string.substring(0, keyIndex - 1)         // the last entry; -1 to strip leading comma
                    keyIndex == 0 -> string.substring(valueTo + 1)  // the first entry; +1 to strip trailing comma
                    else -> "${string.substring(0, keyIndex)}${string.substring(valueTo + 1)}"    // middle entry; +1 to avoid double-comma
                }
            }
        }
        //-----------------------------------------------------------------------------------------
        //endregion

        //region Internal Utility Helpers
        //-----------------------------------------------------------------------------------------
        /**
         * Returns true iff all parameters are non-null
         */
        private fun nn(vararg values: Int?) = values.all { it != null }

        /**
         * Returns true iff all parameters are non-null and have increasing value
         */
        private fun nnLT(vararg values: Int?): Boolean {
            if (values.isEmpty()) return true
            if (values[0] == null) return false
            for (i in 1 until values.size) {
                val left = values[i - 1]!!
                val right = values[i]
                if (right == null || left >= right) return false
            }

            return true
        }

        /**
         * Returns true iff all parameters are non-null and have non-decreasing value
         */
        private fun nnLTE(vararg values: Int?): Boolean {
            if (values.isEmpty()) return true
            if (values[0] == null) return false
            for (i in 1 until values.size) {
                val left = values[i - 1]!!
                val right = values[i]
                if (right == null || left > right) return false
            }

            return true
        }

        /**
         * Returns true iff all non-null parameters are given in ascending order.
         */
        private fun nullOrLT(vararg values: Int?): Boolean {
            val nonNull = values.filterNotNull()
            for (i in 1 until nonNull.size) {
                val left = values[i - 1]!!
                val right = values[i]!!
                if (left >= right) return false
            }
            return true
        }

        /**
         * Returns true iff all non-null parameters are given in non-decreasing order.
         */
        private fun nullOrLTE(vararg values: Int?): Boolean {
            val nonNull = values.filterNotNull()
            for (i in 1 until nonNull.size) {
                val left = nonNull[i - 1]
                val right = nonNull[i]
                if (left > right) return false
            }
            return true
        }

        private class ReusableEntry(key: Int, value: Int): Map.Entry<Int, Int> {
            private var _key: Int = key
            private var _value: Int = value
            override val key: Int
                get() = _key
            override val value: Int
                get() = _value

            fun set(key: Int, value: Int): ReusableEntry {
                _key = key
                _value = value
                return this
            }
        }
        //-----------------------------------------------------------------------------------------
        //endregion
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Mutator Functions
    //---------------------------------------------------------------------------------------------
    fun setRange(min: Int? = null, max: Int? = null) {
        if (default != 0) {
            throw IllegalStateException("Non-zero default range boundaries cannot be changed; use [reset]")
        }

        if (nnLT(max, min)) {
            throw IllegalArgumentException("Can't set range min greater than range max")
        }

        if (nnLT(_mapMinKey, min) || nnLT(max, _mapMaxKey)) {
            throw IllegalArgumentException("Can't set range boundary to exclude extant values")
        }

        this.min = min
        this.max = max
    }

    fun resetFromString(string: String): IntHistogram {
        val from = mutableMapOf<Int, Int>()
        var min: Int? = null
        var max: Int? = null
        var default: Int = 0

        val parts = string.split(",")
        parts.filter { it.isNotBlank() }.forEach {
            val sides = it.trim().split("=")
            val key = sides[0].substring(1)
            val value = sides[1].toInt()

            when (key) {
                "min" -> min = value
                "max" -> max = value
                "default" -> default = value
                else -> from[key.toInt()] = value
            }
        }

        return this.reset(min = min, max = max, default = default, from = from)
    }

    fun reset(min: Int? = null, max: Int? = null, default: Int = 0, from: Map<out Int, Int>): IntHistogram {
        if (nnLT(max, min)) {
            throw IllegalArgumentException("Can't set range min greater than range max")
        }
        if (default != 0 && (min == null || max == null)) {
            throw IllegalArgumentException("Non-zero default requires explicit range boundaries")
        }

        this.min = min
        this.max = max
        this.default = default

        _map.clear()
        putAll(from)

        return this
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Accessor Functions
    //---------------------------------------------------------------------------------------------
    /**
     * Calculates and returns the sum of all values in the histogram (equivalent
     * to the [total] property). If a range is set, includes even default values within it.
     *
     * @return The calculated sum.
     */
    fun sum() = total

    /**
     * Calculates and returns the sum of all values in the histogram that pass the provided
     * predicate. If a range is set, includes even default values within it.
     *
     * @return The calculated sum.
     */
    fun sum(predicate: (Map.Entry<Int, Int>) -> Boolean): Int {
        val minKey = this.min
        val maxKey = this.max

        if (minKey == null || maxKey == null || default == 0) {
            return _map.entries
                .filter(predicate)
                .fold(0) { acc, entry -> acc + entry.value }
        }

        val entry = ReusableEntry(0, 0)
        var summation = 0
        for (i in minKey..maxKey) {
            entry.set(i, get(i))
            if (predicate(entry)) {
                summation += entry.value
            }
        }
        return summation
    }

    /**
     * Calculates and returns the sum of all values whose keys appear in the provided iterable,
     * whether set to a default value or not.
     *
     * @return The calculated sum.
     */
    fun sum(keys: Iterable<Int>, predicate: (Map.Entry<Int, Int>) -> Boolean = { true }): Int {
        var workingSum = 0
        val entry = ReusableEntry(0, 0)
        for (i in keys) {
            entry.set(i, get(i))
            if (predicate(entry)) workingSum += entry.value
        }
        return workingSum
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Operators and Object Overrides
    //---------------------------------------------------------------------------------------------
    override fun toString(): String {
        var prefix = ">default=$default"
        if (min != null) prefix = "$prefix,>min=$min"
        if (max != null) prefix = "$prefix,>max=$max"

        return if (_map.isEmpty()) prefix else {
            "$prefix,${_map.entries.joinToString(",") { ">${it.key}=${it.value}" }}"
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Map Interface
    //---------------------------------------------------------------------------------------------
    override val size: Int
        get() = if (nn(min, max)) max!! - min!! + 1 else _map.size

    override fun containsKey(key: Int) = _map.containsKey(key) || nnLTE(min, key, max)

    override fun containsValue(value: Int) = when {
        _map.containsValue(value) -> true
        value == default -> {
            val minBound = min
            val maxBound = max
            minBound != null && maxBound != null && _map.size < maxBound - minBound + 1
        }
        else -> false
    }

    override fun get(key: Int) = getOrDefault(key, 0)

    override fun getOrDefault(key: Int, defaultValue: Int) =
        _map[key] ?: if (nnLTE(min, key, max)) default else defaultValue

    override fun isEmpty() = _map.isEmpty() && !nn(min, max)

    /**
     * Provide the keys in this IntHistogram. If an explicit bounded range is specified, will
     * contain all keys within that range.
     *
     * Note: although the property is mutable (to conform to standard), changes to it will not
     * affect the IntHistogram, and vice-versa.
     */
    override val keys: MutableSet<Int>
        get() {
            val minKey = min
            val maxKey = max
            val result = _map.keys.toMutableSet()
            if (minKey != null && maxKey != null) {
                for (i in minKey..maxKey) {
                    if (i !in result) {
                        result.add(i)
                    }
                }
            }
            return result
        }

    /**
     * Provide the values in this IntHistogram. If an explicit bounded range is specified, will
     * contain all values within that range.
     *
     * Note: although the property is mutable (to conform to standard), changes to it will not
     * affect the IntHistogram, and vice-versa.
     */
    override val values: MutableCollection<Int>
        get() {
            val minKey = min
            val maxKey = max
            val result = _map.values.toMutableList()
            if (minKey != null && maxKey != null) {
                val size = maxKey - minKey + 1
                for (i in 0..(size - _mapTotal)) {
                    result.add(default)
                }
            }
            return result
        }

    /**
     * Provide the entries in this IntHistogram. If an explicit bounded range is specified, will
     * contain all entries within that range.
     *
     * Note: although the property is mutable (to conform to standard), changes to it will not
     * affect the IntHistogram, and vice-versa.
     */
    override val entries: MutableSet<MutableMap.MutableEntry<Int, Int>>
        get() {
            val minKey = min
            val maxKey = max
            val result = _map.toMutableMap()
            if (minKey != null && maxKey != null) {
                for (i in minKey..maxKey) {
                    if (!result.containsKey(i)) {
                        result[i] = default
                    }
                }
            }
            return result.entries
        }

    override fun clear() {
        _map.clear()
        _mapTotal = 0
        _mapMinKey = null
        _mapMaxKey = null
    }

    override fun put(key: Int, value: Int): Int {
        if (!nullOrLTE(min, key, max)) {
            throw IllegalArgumentException("Insertion keys must fit within non-null min/max boundaries")
        }

        return if (value != default) {
            _mapTotal += value - (_map[key] ?: 0)
            if (_mapMinKey == null || _mapMinKey!! > key) _mapMinKey = key
            if (_mapMaxKey == null || _mapMaxKey!! < key) _mapMaxKey = key

            _map.put(key, value) ?: default
        } else {
            remove(key)
        }
    }

    override fun putAll(from: Map<out Int, Int>) {
        if (_map.isNotEmpty()) {
            // update metadata with call
            from.entries.forEach { put(it.key, it.value) }
        } else {
            // manually update metadata for efficiency
            if ((min != null && from.keys.any { it < min!! }) || (max != null && from.keys.any { it > max!! })) {
                throw IllegalArgumentException("Insertion keys must fit within non-null min/max boundaries")
            }
            val included = from.filter { it.value != default }
            val keys = included.keys
            _mapTotal = included.values.sum()
            _mapMinKey = keys.minOrNull()
            _mapMaxKey = keys.maxOrNull()
            _map.putAll(included)
        }

    }

    override fun remove(key: Int): Int {
        _mapTotal -= _map[key] ?: 0
        val removed = _map.remove(key) ?: if (nullOrLTE(min, key, max)) default else 0

        if (key == _mapMinKey) _mapMinKey = _map.keys.minOrNull()
        if (key == _mapMaxKey) _mapMaxKey = _map.keys.maxOrNull()

        return removed
    }

    override fun remove(key: Int, value: Int): Boolean {
        return if (get(key) != value) false else {
            remove(key)
            true
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}