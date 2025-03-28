import * as React from "react";
import useSWR, {mutate} from "swr";
import {fetcher} from "../utils";
import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import {MediaLibraryCard} from "../components/MediaLibraryCard";
import axios from "axios";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import {MainHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import DuplicateResponse = IoSebi.DuplicateResponse;
import DuplicatesDTO = IoSebi.DuplicatesDTO;

/**
 * Hook to fetch all media library entries
 */
function useAllMediaLibraryEntries() {
    const {data, error} = useSWR<Array<AutoTaggedMediaLibraryEntry>>(
        "/api/mediaLibrary?auto=true",
        fetcher
    );

    return {
        mediaLibraryEntries: data || [],
        isLoading: !error && !data,
        isError: error,
    };
}

/**
 * Hook to fetch duplicate data from the API using the top duplicates endpoint
 * Optimized to reduce the number of API calls by fetching all media library entries at once
 */
function useDuplicatesData() {
    // Fetch all media library entries in a single request
    const {mediaLibraryEntries, isLoading: isLoadingEntries, isError: isErrorEntries} = useAllMediaLibraryEntries();

    // Fetch the top duplicates
    const {
        data: duplicateDTOs,
        error: duplicatesError
    } = useSWR<Array<DuplicatesDTO>>('/api/mediaLibrary/duplicates', fetcher, {
        revalidateOnFocus: false,
        dedupingInterval: 10000,
        refreshInterval: 30000
    });

    // Create a map of media library entries by ID for quick lookup
    const entriesMap = React.useMemo(() => {
        const map = new Map<string, MediaLibraryEntry>();
        mediaLibraryEntries.forEach(entry => {
            map.set(entry.mediaLibraryEntry.id, entry.mediaLibraryEntry);
        });
        return map;
    }, [mediaLibraryEntries]);

    // Join the duplicates with their corresponding media library entries
    const duplicatesData = React.useMemo(() => {
        if (!duplicateDTOs || !mediaLibraryEntries.length) return [];

        // Create duplicate responses by joining the data
        const duplicatesWithEntries = duplicateDTOs.map(dto => {
            const sourceEntry = entriesMap.get(dto.src_id);
            const dupEntry = entriesMap.get(dto.dup_id);

            if (!sourceEntry || !dupEntry) return null;

            return {
                entry: sourceEntry,
                possibleDuplicate: dupEntry,
                distance: dto.distance
            };
        }).filter(Boolean) as DuplicateResponse[];

        // Filter out repeated duplicates (where src:A, dst:B and src:B, dst:A exist)
        const seenPairs = new Set<string>();
        return duplicatesWithEntries.filter(duplicate => {
            // Create a unique identifier for each pair, sorted to handle both directions
            const ids = [duplicate.entry.id, duplicate.possibleDuplicate.id].sort();
            const pairKey = ids.join('-');

            // If we've seen this pair before, filter it out
            if (seenPairs.has(pairKey)) {
                return false;
            }

            // Otherwise, add it to the set and keep it
            seenPairs.add(pairKey);
            return true;
        });
    }, [duplicateDTOs, mediaLibraryEntries, entriesMap]);

    return {
        duplicates: duplicatesData || [],
        isLoading: (!duplicatesError && !duplicateDTOs) || isLoadingEntries,
        isError: duplicatesError || isErrorEntries
    };
}

/**
 * Hook to update a media library entry
 */
function useMediaLibraryEntryUpdate(entry: MediaLibraryEntry) {
    const id = entry.id;
    const endpoint = `/api/mediaLibrary/${id}?auto=true`;

    const updateEntry = (newEntry: Partial<MediaLibraryEntry>) => {
        const joinedEntry = {...entry, ...newEntry} as MediaLibraryEntry;

        axios.post(`/api/mediaLibrary/${id}`, joinedEntry).then(() => {
            // Only mutate the necessary endpoints
            mutate(endpoint);
            mutate('/api/mediaLibrary?auto=true');
        });
    };

    return {
        updateEntry
    };
}

/**
 * Component to display a single duplicate pair
 */
function DuplicatePair({duplicate}: { duplicate: DuplicateResponse }) {
    const {updateEntry} = useMediaLibraryEntryUpdate(duplicate.possibleDuplicate);

    const isMarkedForDeletion = duplicate.possibleDuplicate.markedForDeletion || false;

    return (
        <div className="w-full mb-8 p-4 bg-white dark:bg-gray-800 rounded-xl shadow-md">
            <div className="flex flex-col md:flex-row items-center justify-between gap-4">
                {/* Source Media Card */}
                <div className="w-full md:w-2/5">
                    <MediaLibraryCard item={duplicate.entry}/>
                </div>

                {/* Distance Display */}
                <div className="flex flex-col items-center justify-center">
                    <div className="text-lg font-bold mb-2 dark:text-gray-200">
                        Distance
                    </div>
                    <div className="text-2xl font-mono dark:text-gray-100">
                        {duplicate.distance}
                    </div>
                </div>

                {/* Destination Media Card */}
                <div className="w-full md:w-2/5">
                    <MediaLibraryCard item={duplicate.possibleDuplicate}/>
                </div>

                {/* Mark to Delete Button */}
                <div className="flex flex-col items-center">
                    <button
                        className={`${commonStyles.greenButton} ${isMarkedForDeletion ? 'bg-red-500 hover:bg-red-600 dark:bg-red-600 dark:hover:bg-red-700' : ''}`}
                        onClick={() => updateEntry({markedForDeletion: !isMarkedForDeletion})}
                    >
                        {isMarkedForDeletion ? "Unmark" : "Mark to Delete"}
                    </button>
                </div>
            </div>
        </div>
    );
}

/**
 * Main component for managing duplicates
 */
export function DuplicatesManager() {
    const {duplicates, isLoading, isError} = useDuplicatesData();

    return (
        <div className="container mx-auto px-4">
            <MainHeading>Duplicates Manager</MainHeading>

            {isLoading && <div className="text-center py-8">Loading duplicates...</div>}
            {isError && <div className="text-center py-8 text-red-500">Error loading duplicates</div>}

            {duplicates.length === 0 && !isLoading && (
                <div className="text-center py-8 dark:text-gray-300">No duplicates found</div>
            )}

            <div className="flex flex-col gap-4">
                {duplicates.filter((duplicate): duplicate is DuplicateResponse => duplicate !== null).map((duplicate) => (
                    <DuplicatePair key={`${duplicate.entry.id}-${duplicate.possibleDuplicate.id}`}
                                   duplicate={duplicate}/>
                ))}
            </div>
        </div>
    );
}
