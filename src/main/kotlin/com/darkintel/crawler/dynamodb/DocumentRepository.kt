package com.darkintel.crawler.dynamodb

import com.darkintel.crawler.model.NormalizedDocument

interface DocumentRepository {

    /**
     * (sourceId, url) 조합에 대해 기존 레코드가 없을 때만
     * 새 문서 레코드를 저장한다.
     *
     * 반환값:
     * - true  : 새 항목이 삽입된 경우(처음 보는 문서)
     * - false : 이미 항목이 존재하는 경우(이전에 수집된 문서)
     */
    suspend fun insertIfNew(doc: NormalizedDocument): Boolean
}
