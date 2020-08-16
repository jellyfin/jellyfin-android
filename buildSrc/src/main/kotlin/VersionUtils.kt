/**
 * Get the versionCode for a given semver
 *
 * Sample output:
 * 0.0.0 -> 0
 * 1.1.1 -> 10101
 * 0.7.0 -> 700
 * 99.99.99 -> 999999
 *
 * @return the versionCode, or null if value is invalid.
 */
fun getVersionCode(versionName: String): Int? {
    val parts = versionName
        .substringBefore('-')
        .splitToSequence('.')
        .mapNotNull(String::toIntOrNull)
        .take(3)
        .toList()

    // Not a valid semver
    if (parts.size != 3) return null

    var code = 0
    code += parts[0] * 10000 // Major (0-99)
    code += parts[1] * 100 // Minor (0-99)
    code += parts[2] // Patch (0-99)

    return code
}