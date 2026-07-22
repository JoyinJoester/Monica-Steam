package takagi.ru.monica.security

private const val MASK_DOTS = "••••••"

/**
 * Produces a fixed-length visual hint without revealing the original password length.
 */
fun securityPasswordMask(password: String): String = when (password.length) {
    0 -> MASK_DOTS
    1 -> "${password.first()}$MASK_DOTS"
    else -> "${password.first()}$MASK_DOTS${password.last()}"
}
