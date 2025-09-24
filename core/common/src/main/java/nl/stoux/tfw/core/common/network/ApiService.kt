package nl.stoux.tfw.core.common.network

import nl.stoux.tfw.core.common.network.dto.EditionDto
import retrofit2.http.GET

interface ApiService {
    /**
     * Fetch the entire catalog tree in a single call.
     */
    @GET("editions")
    suspend fun getEditions(): List<EditionDto>
}
