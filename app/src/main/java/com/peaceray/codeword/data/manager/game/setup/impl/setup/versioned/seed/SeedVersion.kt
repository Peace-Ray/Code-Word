package com.peaceray.codeword.data.manager.game.setup.impl.setup.versioned.seed

/**
 * The SeedVersions available when interpreting game seeds.
 *
 * Note: conversion between enum and integer uses explicit constructor arguments as ordinals rather
 * than the generated [ordinal] property. [ordinal] is implicit from the order versions are
 * specified, and relying on it as a permanent indicator that persists between class updates is
 * dangerous. It also disallows "gaps" in numeric encodings, which are convenient for two reasons:
 * protecting against accidental version transcription errors, and easy encoding with
 * 0-padded concatenation.
 *
 * In particular, [SeedVersion.numberEncoding] should be very different between instances
 * (i.e. prefer large Hamming Distance across all valid inputs), and must not contain '0's in
 * Base-10.
 *
 * For convenience, here are the first 500 integers (starting from '1') that meet these criteria:
 *      1. No '0's in Base-10 representation
 *      2. Mutual Hamming Distance of at least 5 (ascending order greedy algorithm)
 * When a new SeedVersion is added, use the next number on this list as its numberEncoding.
 *
 * 1, 46, 212, 251, 786, 829, 967, 1352, 1398, 1439, 1635, 1674, 1956, 2417, 2444, 2637, 2736, 2922,
 * 3239, 3838, 4514, 4589, 4696, 4791, 5168, 5199, 5585, 6166, 6283, 7429, 7483, 7724, 7872, 8632,
 * 9244, 9873, 9965, 11163, 11266, 11631, 11744, 11863, 12371, 12388, 12766, 12841, 13546, 14119,
 * 14164, 14525, 14665, 15118, 15331, 15798, 17128, 17533, 17833, 17988, 18267, 18456, 18626, 19239,
 * 21133, 21313, 21374, 21638, 22258, 22424, 22999, 23157, 23772, 24322, 24559, 24626, 24741, 24843,
 * 24917, 25246, 25472, 26615, 27259, 27644, 27849, 28193, 28445, 28586, 29851, 29996, 31398, 31536,
 * 33673, 34739, 35142, 35539, 35881, 36112, 36299, 36893, 37582, 37636, 37872, 38332, 38698, 39137,
 * 39775, 41111, 41315, 41524, 42437, 42511, 42618, 43178, 43584, 43941, 45184, 46457, 46787, 47143,
 * 48222, 48665, 48884, 49246, 49636, 51138, 51382, 51723, 52339, 52526, 52728, 52869, 53288, 53523,
 * 54631, 54847, 55993, 56264, 58188, 58448, 59158, 59793, 61134, 61679, 62165, 62394, 62469, 62794,
 * 63393, 63772, 64911, 65122, 67548, 68697, 68924, 69387, 69477, 71187, 71417, 71778, 72152, 72577,
 * 72638, 73158, 73928, 73988, 74159, 74353, 74591, 74811, 75154, 75462, 77416, 78484, 78658, 78989,
 * 79672, 81139, 81461, 81562, 81943, 82296, 82382, 82543, 82865, 83124, 83234, 84756, 85562, 86538,
 * 87137, 87325, 88236, 88795, 89233, 89671, 92173, 92276, 93125, 93495, 94688, 95318, 96142, 96554,
 * 97627, 98256, 98379, 98978, 99334, 99564, 99864, 111181, 111377, 111532, 112639, 112688, 113141,
 * 113729, 113965, 114594, 114841, 114989, 116183, 116342, 116481, 117155, 117345, 117468, 117898,
 * 118717, 118852, 119291, 119687, 121682, 122228, 122518, 122636, 123851, 124131, 124316, 124584,
 * 125446, 125682, 127286, 127523, 129351, 129913, 133244, 133766, 134813, 135122, 135145, 135947,
 * 136734, 136865, 137512, 137575, 137745, 138485, 139471, 139575, 141121, 141246, 141521, 141658,
 * 141871, 143632, 144124, 144261, 145371, 146122, 146564, 146994, 147325, 147718, 147929, 148115,
 * 148586, 149167, 149296, 149941, 151374, 151587, 151742, 152532, 152914, 154633, 155256, 155571,
 * 156226, 157132, 158182, 158782, 159227, 159444, 159623, 159865, 161357, 161938, 162643, 162729,
 * 163169, 163936, 163994, 164173, 164471, 164524, 165123, 165756, 166319, 168314, 169161, 169174,
 * 171527, 171771, 171872, 171924, 172633, 173222, 173241, 174882, 175125, 175496, 176142, 177126,
 * 178563, 178847, 181376, 183125, 183198, 183519, 183826, 183917, 184716, 185892, 185937, 186442,
 * 187111, 187845, 188882, 189153, 189373, 192344, 192863, 193975, 194186, 194678, 195816, 196777,
 * 196838, 196947, 197242, 197568, 198149, 198666, 199671, 199766, 211229, 211436, 211524, 212395,
 * 214729, 214934, 215297, 215482, 215613, 216288, 217461, 218499, 218921, 218982, 219216, 219981,
 * 221732, 221976, 222225, 222426, 222535, 223331, 223838, 223883, 225138, 225477, 226111, 226992,
 * 227583, 229684, 231152, 231199, 231475, 231557, 231929, 232638, 234339, 234386, 234461, 234539,
 * 234912, 235629, 236168, 236826, 237148, 237934, 238377, 238693, 239492, 241227, 241313, 241883,
 * 242748, 243282, 245486, 247781, 249154, 251133, 251935, 252836, 253823, 255119, 257145, 257316,
 * 258664, 259537, 261598, 261632, 265316, 265725, 267123, 268163, 268264, 268415, 268956, 271342,
 * 271649, 272462, 272563, 272692, 274136, 274975, 275738, 275968, 276413, 276677, 278123, 278938,
 * 279763, 279828, 281174, 281279, 281547, 281615, 281921, 282917, 284389, 284912, 285465, 285866,
 * 286644, 287593, 287847, 288312, 289941, 291142, 292867, 293256, 293484, 294258, 294481, 294878,
 * 295143, 295227, 295645, 295989, 296598, 296782, 296785, 297376, 297592, 297974, 299691, 311111,
 * 311298, 313892, 315736, 315975, 316937, 317934, 318524, 318866, 319628, 319737, 321433, 321883,
 * 321893, 322366, 323328, 323555, 324468, 324659, 326288, 327341, 328118
 */
enum class SeedVersion(val numberEncoding: Int) {
    /**
     * Initial version. Launch.
     */
    V1(1),

    /**
     * Update version; adds "characterOccurrences" (either 1 or word length) and the ability to
     * set ConstraintPolicy within a language.
     */
    V2(46);

    // next version: V3(212)

    init {
        if (numberEncoding != 0 && numberEncoding.toString(10).contains("0")) {
            throw IllegalArgumentException("SeedVersion numberEncodings must not contain '0'")
        }
    }

    companion object {
        /**
         * Returns the [SeedVersion] specified by this number encoding, if any exists.
         *
         * @param numberEncoding The numerical encoding of a SeedVersion
         * @return The SeedVersion encoded
         * @throws IllegalArgumentException If [numberEncoding] is not recognized
         * (if [isNumberEncoding] would have returned false)
         */
        fun forNumberEncoding(numberEncoding: Int): SeedVersion {
            for (v in SeedVersion.values()) {
                if (v.numberEncoding == numberEncoding) return v
            }
            throw IllegalArgumentException("Encoding number $numberEncoding not recognized")
        }

        /**
         * Determines if any known [SeedVersion] is specified by the provided number encoding.
         *
         * @param numberEncoding The numerical encoding of a SeedVersion
         * @return Whether a SeedVersion is actually encoded by the given number
         * (and [forNumberEncoding] would succeed).
         */
        fun isNumberEncoding(numberEncoding: Int) = values().any { it.numberEncoding == numberEncoding }
    }
}

/**
 * Convenience class for implementing versioned game rules components. Versioned components
 * may extend from this class for access to an immutable "seedVersion" property; this is not required
 * but may be more useful than keeping references to that version inlined where they may be
 * potentially affected by typos.
 */
abstract class VersionedBySeed(val seedVersion: SeedVersion)