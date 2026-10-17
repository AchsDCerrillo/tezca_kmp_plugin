package __PKG__.core.common.result

/** Every failure the data layer can report. Map it to user-facing text in the UI layer. */
public sealed interface AppError {

    public sealed interface Network : AppError {
        public data object NoInternet : Network
        public data object Timeout : Network
        public data object Unauthorized : Network
        public data object Forbidden : Network
        public data object NotFound : Network
        public data object Serialization : Network
        public data class Client(val code: Int, val body: String?) : Network
        public data class Server(val code: Int, val body: String?) : Network
    }

    public sealed interface Local : AppError {
        public data object NotFound : Local
        public data class Database(val message: String?) : Local
    }

    /**
     * A business rule failed (invalid coupon, plan already active...). [code] is a stable key the
     * UI can map to a message; [message] is optional detail for logs or a server-provided text.
     */
    public data class Domain(val code: String, val message: String? = null) : AppError

    public data class Unknown(val message: String?) : AppError
}
