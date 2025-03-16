package io.sebi.storage

/**
 * Interface for storage of duplicate entries.
 * Provides CRUD operations for duplicates containing sourceId, destinationId, and distance.
 */
interface DuplicatesStorage {
    /**
     * Creates a new duplicate entry.
     *
     * @param sourceId The ID of the source media
     * @param destinationId The ID of the destination media (duplicate)
     * @param distance The distance between the source and destination
     */
    suspend fun createDuplicate(sourceId: String, destinationId: String, distance: Int)

    /**
     * Retrieves a duplicate entry by source ID.
     *
     * @param sourceId The ID of the source media
     * @return The duplicate entry, or null if not found
     */
    suspend fun getDuplicateBySourceId(sourceId: String): Duplicates?

    /**
     * Retrieves all duplicate entries for a source ID.
     *
     * @param sourceId The ID of the source media
     * @return A list of duplicate entries
     */
    suspend fun getAllDuplicatesForSourceId(sourceId: String): List<Duplicates>

    /**
     * Updates an existing duplicate entry.
     *
     * @param sourceId The ID of the source media
     * @param destinationId The ID of the destination media (duplicate)
     * @param distance The new distance between the source and destination
     * @return True if the update was successful, false otherwise
     */
    suspend fun updateDuplicate(sourceId: String, destinationId: String, distance: Int): Boolean

    /**
     * Deletes a duplicate entry.
     *
     * @param sourceId The ID of the source media
     * @param destinationId The ID of the destination media (duplicate)
     * @return True if the deletion was successful, false otherwise
     */
    suspend fun deleteDuplicate(sourceId: String, destinationId: String): Boolean

    /**
     * Deletes all duplicate entries for a source ID.
     *
     * @param sourceId The ID of the source media
     * @return The number of entries deleted
     */
    suspend fun deleteAllDuplicatesForSourceId(sourceId: String): Int
}