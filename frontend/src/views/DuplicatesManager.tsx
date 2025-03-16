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

/**
 * Hook to fetch duplicate data from the API using storedDuplicate endpoint
 */
function useDuplicatesData() {
    // First get all media library entries
    const {
        data: mediaLibraryEntries,
        error: mediaLibraryError
    } = useSWR<Array<MediaLibraryEntry>>(`/api/mediaLibrary`, fetcher);

    // Then for each entry, get its stored duplicate if it exists
    const duplicatesPromises = mediaLibraryEntries?.map(entry => {
        return fetch(`/api/mediaLibrary/${entry.id}/storedDuplicate`)
            .then(response => {
                if (!response.ok) {
                    if (response.status === 404) {
                        // No duplicate found for this entry, which is fine
                        return null;
                    }
                    throw new Error(`Error fetching duplicate for ${entry.id}: ${response.statusText}`);
                }
                return response.json();
            })
            .then(duplicateDTO => {
                if (!duplicateDTO) return null;

                // Now fetch the duplicate entry details
                return fetch(`/api/mediaLibrary/${duplicateDTO.dup_id}?auto=true`)
                    .then(response => {
                        if (!response.ok) {
                            throw new Error(`Error fetching duplicate entry ${duplicateDTO.dup_id}: ${response.statusText}`);
                        }
                        return response.json();
                    })
                    .then(duplicateEntry => {
                        if (!duplicateEntry) return null;

                        // Create a DuplicateResponse-like object
                        return {
                            entry: entry,
                            possibleDuplicate: duplicateEntry.mediaLibraryEntry,
                            distance: duplicateDTO.distance
                        };
                    });
            })
            .catch(error => {
                console.error(error);
                return null;
            });
    }) || [];

    // Use SWR's useSWR to handle the promises
    const {data: duplicatesData, error: duplicatesError} = useSWR(
        mediaLibraryEntries ? 'storedDuplicates' : null,
        async () => {
            const results = await Promise.all(duplicatesPromises);
            return results.filter(Boolean); // Remove null entries
        },
        {
            revalidateOnFocus: false,
            dedupingInterval: 10000,
            refreshInterval: 30000
        }
    );

    // Sort duplicates by distance (lowest first) and take top 50
    const sortedDuplicates = duplicatesData
        ? [...duplicatesData].sort((a, b) => (a?.distance || 0) - (b?.distance || 0)).slice(0, 50)
        : [];

    return {
        duplicates: sortedDuplicates,
        isLoading: (!mediaLibraryError && !mediaLibraryEntries) || (!duplicatesError && !duplicatesData),
        isError: mediaLibraryError || duplicatesError
    };
}

/**
 * Hook to fetch and update a media library entry
 */
function useMediaLibraryEntry(id: string) {
    const endpoint = `/api/mediaLibrary/${id}?auto=true`;
    const {data, error} = useSWR<AutoTaggedMediaLibraryEntry>(endpoint, fetcher);

    return {
        entry: data,
        isLoading: !error && !data,
        isError: error,
        mutateEntry: (newEntry: Partial<MediaLibraryEntry>) => {
            if (!data?.mediaLibraryEntry) return;
            const joinedEntry = {...data.mediaLibraryEntry, ...newEntry} as MediaLibraryEntry;

            axios.post(`/api/mediaLibrary/${id}`, joinedEntry).then(() => {
                mutate(endpoint);
                // Also mutate the duplicates list to reflect changes
                mutate('storedDuplicates');
            });
        }
    };
}

/**
 * Component to display a single duplicate pair
 */
function DuplicatePair({duplicate}: { duplicate: DuplicateResponse }) {
    const {entry, isLoading, isError, mutateEntry} = useMediaLibraryEntry(duplicate.possibleDuplicate.id);

    if (isLoading) return <div>Loading...</div>;
    if (isError) return <div>Error loading duplicate</div>;

    const isMarkedForDeletion = entry?.mediaLibraryEntry.markedForDeletion || false;

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
                        onClick={() => mutateEntry({markedForDeletion: !isMarkedForDeletion})}
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
