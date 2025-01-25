import React, {useEffect, useState} from 'react';
import {MediaLibraryEntry} from "../models/MediaLibraryEntry";

interface DebugResponse {
    status: 'success' | 'error';
    debug_output: string[];
}

export const ThumbnailDebug: React.FC = () => {
    const [entriesWithoutThumbnails, setEntriesWithoutThumbnails] = useState<MediaLibraryEntry[]>([]);
    const [debugOutput, setDebugOutput] = useState<string[]>([]);
    const [processingIds, setProcessingIds] = useState<Set<string>>(new Set());
    const [error, setError] = useState<string | null>(null);

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
        <div className="container mx-auto p-4">
            <h1 className="text-2xl font-bold mb-4">Thumbnail Debug</h1>

            {error && (
                <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
                    {error}
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                    <h2 className="text-xl font-semibold mb-2">Videos Without Thumbnails</h2>
                    {entriesWithoutThumbnails.length === 0 ? (
                        <p>No videos found without thumbnails.</p>
                    ) : (
                        <ul className="space-y-2">
                            {entriesWithoutThumbnails.map((entry) => (
                                <li key={entry.id} className="border p-3 rounded">
                                    <div className="flex justify-between items-center">
                                        <span>{entry.name}</span>
                                        <button
                                            onClick={() => regenerateThumbnail(entry.id)}
                                            disabled={processingIds.has(entry.id)}
                                            className="bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600 disabled:opacity-50"
                                        >
                                            {processingIds.has(entry.id) ? 'Regenerating...' : 'Regenerate'}
                                        </button>
                                    </div>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                <div>
                    <h2 className="text-xl font-semibold mb-2">Debug Output</h2>
                    <div className="bg-gray-100 p-4 rounded h-[500px] overflow-y-auto font-mono text-sm">
                        {debugOutput.length === 0 ? (
                            <p className="text-gray-500">No debug output available.</p>
                        ) : (
                            debugOutput.map((line, index) => (
                                <div key={index}
                                     className={`${line.includes('[ERROR]') ? 'text-red-600' : 'text-gray-800'}`}>
                                    {line}
                                </div>
                            ))
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};
