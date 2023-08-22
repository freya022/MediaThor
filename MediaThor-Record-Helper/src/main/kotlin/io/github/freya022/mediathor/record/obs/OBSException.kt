package io.github.freya022.mediathor.record.obs

class OBSException(code: Int, comment: String?) : Exception("Code: $code, comment: $comment")
