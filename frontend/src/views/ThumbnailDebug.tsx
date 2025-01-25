import React, {memo, useEffect, useMemo, useState} from 'react';
import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import axios from "axios";
import useSWR, {mutate} from "swr";
import {fetcher} from "../utils";
import {useMediaInfo} from "../hooks/useMediaInfo";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import {VList} from 'virtua';

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
            });
        }
    };
}


interface DebugResponse {
    status: 'success' | 'error';
    debug_output: string[];
}

interface EntryItemProps {
    entry: MediaLibraryEntry;
    onRegenerate: (id: string) => void;
    isProcessing: boolean;
}

interface DebugOutputProps {
    debugOutput: string[];
}

const DebugLine = memo(({line}: { line: string }) => (
    <div
        className={`py-1 ${line.includes('[ERROR]') ? 'text-red-600 dark:text-red-400' : 'text-gray-800 dark:text-gray-300'}`}>
        {line}
    </div>
));

const DebugOutput: React.FC<DebugOutputProps> = ({debugOutput}) => {
    const maxLines = 1000;
    const limitedOutput = useMemo(() => {
        return debugOutput.slice(-maxLines);
    }, [debugOutput]);

    const truncatedLines = debugOutput.length - maxLines;

    return (
        <div>
            <h2 className="text-xl font-semibold mb-2 dark:text-gray-200">Debug Output</h2>
            <div className="bg-gray-100 dark:bg-gray-800 p-4 rounded h-[500px] font-mono text-sm">
                {debugOutput.length === 0 ? (
                    <p className="text-gray-500 dark:text-gray-400">No debug output available.</p>
                ) : (
                    <>
                        {truncatedLines > 0 && (
                            <div className="text-gray-500 dark:text-gray-400 mb-2 text-xs">
                                Showing last {maxLines} lines ({truncatedLines} earlier lines hidden)
                            </div>
                        )}
                        <VList style={{height: truncatedLines > 0 ? '425px' : '450px'}}>
                            {limitedOutput.map((line, index) => (
                                <DebugLine key={index} line={line}/>
                            ))}
                        </VList>
                    </>
                )}
            </div>
        </div>
    );
};

const EntryItem: React.FC<EntryItemProps> = ({entry, onRegenerate, isProcessing}) => {
    const {entry: mediaEntry, mutateEntry} = useMediaLibraryEntry(entry.id);
    const {isAudio} = useMediaInfo(entry.id);

    return (
        <li className="border dark:border-gray-700 p-3 rounded dark:bg-gray-800/50">
            <div className="flex justify-between items-center gap-4">
                <div className="flex items-center gap-2 min-w-0">
                    {isAudio && (
                        <svg className="w-5 h-5 text-blue-500 shrink-0" fill="none" stroke="currentColor"
                             viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                  d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z"/>
                        </svg>
                    )}
                    <span className="truncate dark:text-gray-200">{entry.name}</span>
                </div>
                <div className="flex gap-2 shrink-0">
                    <a
                        href={`/#/movie/${entry.id}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="bg-gray-500 text-white p-2 rounded hover:bg-gray-600 dark:bg-gray-600 dark:hover:bg-gray-700 flex items-center justify-center"
                        title="Open media page"
                    >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"
                             xmlns="http://www.w3.org/2000/svg">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                  d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/>
                        </svg>
                    </a>
                    <button
                        onClick={() => {
                            if (mediaEntry?.mediaLibraryEntry) {
                                mutateEntry({markedForDeletion: !mediaEntry.mediaLibraryEntry.markedForDeletion});
                            }
                        }}
                        className={`${
                            mediaEntry?.mediaLibraryEntry?.markedForDeletion
                                ? 'bg-green-500 hover:bg-green-600 dark:bg-green-600 dark:hover:bg-green-700'
                                : 'bg-red-500 hover:bg-red-600 dark:bg-red-600 dark:hover:bg-red-700'
                        } text-white p-2 rounded flex items-center justify-center`}
                        title={mediaEntry?.mediaLibraryEntry?.markedForDeletion ? 'Unmark for deletion' : 'Mark for deletion'}
                    >
                        {mediaEntry?.mediaLibraryEntry?.markedForDeletion ? (
                            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"
                                 xmlns="http://www.w3.org/2000/svg">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                      d="M3 10h10a8 8 0 018 8v2M3 10l6 6m-6-6l6-6"/>
                            </svg>
                        ) : (
                            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"
                                 xmlns="http://www.w3.org/2000/svg">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                      d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                            </svg>
                        )}
                    </button>
                    <button
                        onClick={() => onRegenerate(entry.id)}
                        disabled={isProcessing}
                        className="bg-blue-500 text-white p-2 rounded hover:bg-blue-600 dark:bg-blue-600 dark:hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2"
                        title="Regenerate thumbnail"
                    >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"
                             xmlns="http://www.w3.org/2000/svg">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
                        </svg>
                        <span className="text-sm">{isProcessing ? 'Regenerating...' : 'Regenerate'}</span>
                    </button>
                </div>
            </div>
        </li>
    );
};

export const ThumbnailDebug: React.FC = () => {
    const [entriesWithoutThumbnails, setEntriesWithoutThumbnails] = useState<MediaLibraryEntry[]>([]);
    const [debugOutput, setDebugOutput] = useState<string[]>([]);
    const [processingIds, setProcessingIds] = useState<Set<string>>(new Set());
    const [error, setError] = useState<string | null>(null);
    const [searchTerm, setSearchTerm] = useState<string>("");

    const filteredEntries = useMemo(() => {
        if (!searchTerm.trim()) return entriesWithoutThumbnails;
        const lowercaseSearch = searchTerm.toLowerCase();
        return entriesWithoutThumbnails.filter(entry =>
            entry.name.toLowerCase().includes(lowercaseSearch)
        );
    }, [entriesWithoutThumbnails, searchTerm]);

    useEffect(() => {
        fetchEntriesWithoutThumbnails();
    }, []);


    const fetchEntriesWithoutThumbnails = async () => {
        try {
            const response = await fetch('/api/mediaLibrary/debug/missing-thumbnails');
            if (!response.ok) throw new Error('Failed to fetch entries');
            const data = await response.json();
            setEntriesWithoutThumbnails(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Unknown error');
        }
    };

    const regenerateThumbnail = async (id: string) => {
        setProcessingIds(prev => new Set(prev).add(id));
        setDebugOutput([]);
        setError(null);

        const eventSource = new EventSource(`/api/mediaLibrary/debug/regenerate-thumbnail/${id}`, {
            withCredentials: true
        });

        // Set up a timeout to close the connection if nothing happens
        const timeoutId = setTimeout(() => {
            setError('Operation timed out after 5 minutes');
            eventSource.close();
            setProcessingIds(prev => {
                const newSet = new Set(prev);
                newSet.delete(id);
                return newSet;
            });
        }, 5 * 60 * 1000);

        const cleanup = () => {
            clearTimeout(timeoutId);
            eventSource.close();
            setProcessingIds(prev => {
                const newSet = new Set(prev);
                newSet.delete(id);
                return newSet;
            });
        };

        eventSource.onmessage = (event) => {
            // Reset timeout on each message
            clearTimeout(timeoutId);

            setDebugOutput(prev => [...prev, event.data]);
            if (event.data.includes("Thumbnail generation completed")) {
                cleanup();
                fetchEntriesWithoutThumbnails();
            }
        };

        eventSource.onerror = (error) => {
            console.error('SSE Error:', error);
            setError('Connection error occurred');
            cleanup();
        };
    };

    return (
        <div className="container mx-auto p-4 dark:bg-gray-900">
            <h1 className="text-2xl font-bold mb-4 dark:text-white">Thumbnail Debug</h1>

            {error && (
                <div
                    className="bg-red-100 dark:bg-red-900/30 border border-red-400 dark:border-red-500/50 text-red-700 dark:text-red-400 px-4 py-3 rounded mb-4">
                    {error}
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                    <h2 className="text-xl font-semibold mb-2 dark:text-gray-200">Videos Without Thumbnails</h2>
                    <div className="mb-4">
                        <input
                            type="text"
                            placeholder="Filter videos..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="w-full px-4 py-2 rounded border dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
                        />
                    </div>
                    <div className="bg-white dark:bg-gray-800 rounded h-[500px]">
                        {filteredEntries.length === 0 ? (
                            <p className="text-gray-500 dark:text-gray-400">
                                {entriesWithoutThumbnails.length === 0
                                    ? "No videos found without thumbnails."
                                    : `No videos match the filter "${searchTerm}"`}
                            </p>
                        ) : (
                            <VList style={{height: '450px'}} className="space-y-2">
                                {filteredEntries.map((entry) => (
                                    <EntryItem
                                        key={entry.id}
                                        entry={entry}
                                        onRegenerate={regenerateThumbnail}
                                        isProcessing={processingIds.has(entry.id)}
                                    />
                                ))}
                            </VList>
                        )}
                    </div>
                </div>

                <DebugOutput debugOutput={debugOutput}/>
            </div>
        </div>
    );
};
