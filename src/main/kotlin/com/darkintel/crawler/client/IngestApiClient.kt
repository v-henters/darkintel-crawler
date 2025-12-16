package com.darkintel.crawler.client

import com.darkintel.crawler.model.NormalizedDocument

interface IngestApiClient {
    suspend fun send(doc: NormalizedDocument)
}
