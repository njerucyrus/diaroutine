package com.diaroutine.data

import com.diaroutine.data.db.ProfileDao
import com.diaroutine.data.db.toDomain
import com.diaroutine.data.db.toEntity
import com.diaroutine.domain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProfileRepository(private val dao: ProfileDao) {
    /** The saved profile, or defaults (not onboarded) before the first save. */
    val profile: Flow<Profile> = dao.observe().map { it?.toDomain() ?: Profile() }

    private val writes = Mutex()

    suspend fun current(): Profile = dao.get()?.toDomain() ?: Profile()

    suspend fun update(transform: (Profile) -> Profile) = writes.withLock {
        dao.upsert(transform(current()).toEntity())
    }
}
